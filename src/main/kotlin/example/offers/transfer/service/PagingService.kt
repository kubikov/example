package example.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.apache.kafka.common.TopicPartition
import org.jooq.exception.DataAccessException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import example.config.AppConfig
import example.domain.Output
import example.domain.PagingState
import example.domain.Status.*
import example.repository.OfferRepository
import java.time.Duration
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

class PagingService : KoinComponent, ConsumerService<PagingState> {

    private val appConfig by inject<AppConfig>()
    private val pagingStateProducer by inject<PagingStateProducer>()
    private val dataProducer by inject<DataProducer>()
    private val dlqProducer by inject<DlqProducer>()
    private val offerRepository by inject<OfferRepository>()
    private val statusService by inject<StatusService>()
    private val appMicrometerRegistry by inject<PrometheusMeterRegistry>()
    private var statuses: List<PagingState> = listOf()
    private var checkingMode = false
    private var endIndex: Long = 0L
    private var init = true

    private val timer = Timer.builder("offers_transfer.timer")
        .description("indicates processing time")
        .tags("dev", "performance")
        .register(appMicrometerRegistry)

    private val counter = Counter.builder("offers_transfer_counter")
        .description("indicates count of records")
        .tags("dev", "performance")
        .register(appMicrometerRegistry)

    private val counterDlq = Counter.builder("offers_transfer_counter_dlq")
        .description("indicates count of dlq records")
        .tags("dev", "performance")
        .register(appMicrometerRegistry)

    private val counterFailed = Counter.builder("offers_transfer_counter_failed")
        .description("indicates count of dlq records")
        .tags("dev", "performance")
        .register(appMicrometerRegistry)

    /**
     *
     * Checks for fail statuses ('status' topic) and if there are none then
     * push end_id to the next partition in 'paging' topic .
     *
     * Receives data from database by range with start_id and end_id
     * and push to 'data' topic.
     *
     * @property [pagingStateRecords] records received from 'paging' topic .
     * @property partition the partition from received records
     *
     */
    override fun processRecords(pagingStateRecords: List<PagingState>, partition: Int) {

        val time = measureTimeMillis {
            val startIndex = pagingStateRecords.maxBy { it.id }.id
            endIndex = startIndex + appConfig.paging.pageSize - 1 // -1 потому что включительно выбераем
            val nextPartition = if (partition == appConfig.paging.partitionLastIndex) 0 else partition + 1

            runBlocking {
                // ставим статус STARTING с последним индексом
                statusService.setStatus(endIndex, STARTING)

                // отправка в следующую партицию следующего индекса до начала обоработки
                // если сообщения из последней партиции, значит не отправляет
                if (partition != appConfig.paging.partitionLastIndex)
                    pagingStateProducer.sendPagingState(nextPartition, endIndex)

                if (partition == appConfig.paging.partitionLastIndex) {
                    checkingMode = true
                    logger.info { "Checking mode ENABLED" }
                }

                // обработка
                pagingStateRecords.processAndValidate { state ->
                    offerRepository.selectRangeByIndex(state.id, state.id + appConfig.paging.pageSize - 1)
                        .toMutableList()
                }
            }
        }

        timer.record(Duration.ofMillis(time))
    }

    private suspend fun getDataAndSend(startIndex: Long, endIndex: Long) =
        try {

            logger.info { "SELECT ID >= $startIndex AND ID < $endIndex" }

            offerRepository.selectRangeByIndex(startIndex, endIndex).forEach { data ->
                dataProducer.sendData(data)
            }
            PROCESSED
        } catch (e: DataAccessException) {
            logger.error { "Postgresql error: ${e.message}" }
            FAILED
        }

    // делаем и ретрай и формируем список из попыток с ошибками
    private suspend fun retryAll(failedStatuses: List<PagingState>) = failedStatuses.mapNotNull {

        val status = getDataAndSend(it.id - appConfig.paging.pageSize, it.id)
        if (status == FAILED)
            it
        else {
            statusService.setStatus(it.id - 1, status)
            null
        }
    }

    override fun statuses(statusRecords: List<PagingState>, topicPartitions: List<TopicPartition>) {

        statuses = statusRecords

        checkBeginStatus()

        val lastId = statuses.maxByOrNull { it.id }!!.id
        val statusesCount = (appConfig.paging.partitionLastIndex + 1) * 2

        // проверка на старте нужна после первого круга
        if (statuses.size < statusesCount) init = false

        // проверяем на старте незавершенные задачи в кластере в последнем инстансе,
        // который включает последнюю партициию и делаем ретрай
        if (init) {

            // определяем что инстанс слушает последнюю партицию
            val lastPartitionEnabled = topicPartitions.any { it.partition() == appConfig.paging.partitionLastIndex }
            if (lastPartitionEnabled) {
                val searchPartiallyProcessedStatuses = statusService.searchPartiallyProcessedStatuses(statuses)
                val unprocessedStatuses = statusService.searchUnprocessedStatuses(statuses)
                if (unprocessedStatuses.isNotEmpty() && searchPartiallyProcessedStatuses.isEmpty()) {
                    logger.info { "Unprocessed states for RETRY: $unprocessedStatuses" }
                    runBlocking {

                        // ретраем запросы пока не останется зафэйленных задач
                        var failedStatuses: List<PagingState?> = mutableListOf(null)
                        while (failedStatuses.isNotEmpty()) {
                            failedStatuses = retryAll(unprocessedStatuses)
                        }

                        // отдаем задачу следующему инстансу
                        pagingStateProducer.sendPagingState(0, lastId)
                    }
                }
                init = false
            }
        }

        // проверяем что все задачи был выполнены в кластере в последнем инстансе
        if (checkingMode) {

            logger.info { "Checking on status: $lastId, $endIndex" }

            // ожидаем последнего статуса
            if (lastId == endIndex) {

                // проверяем что все статусы кластера были отправлены
                val isAllReturned = statusService.isAllInstancesReturnedStatus(statuses, endIndex)
                if (isAllReturned) {
                    runBlocking {

                        // получаем статусы зафэйленных задач и делаем ретрай пока не будет фэйлов в списке
                        var failedStatuses = statusService.searchFailedStatuses(statuses)
                        if (failedStatuses.isNotEmpty()) {
                            logger.info { "Failed states for RETRY: $failedStatuses" }

                            // метрики
                            counterFailed.increment(failedStatuses.size.toDouble())

                            while (failedStatuses.isNotEmpty()) {
                                failedStatuses = retryAll(failedStatuses)
                            }
                        }

                        checkingMode = false

                        // отдаем задачу следующему инстансу
                        pagingStateProducer.sendPagingState(0, endIndex)
                    }
                }
            }
        }
    }

    private fun checkBeginStatus() {
        val beginStatuses = statuses.filter { it.message == BEGIN.toString() }.sortedBy { it.timestamp }

        if (beginStatuses.isNotEmpty() && beginStatuses.last().id == -1L) {
            val partiallyStatus = statuses.lastOrNull() { it.message == PARTIALLY_PROCESSED.toString() }
            if (partiallyStatus != null) {
                runBlocking {

                    statusService.setStatus(partiallyStatus.id, BEGIN)
                    statusService.setStatus(partiallyStatus.id, PARTIALLY_PROCESSED)
                    pagingStateProducer.sendPagingState(0, partiallyStatus.id)
                }
            }
        }
    }

    private inline fun <T> Collection<T>.processAndValidate(block: (T) -> MutableList<Output>) {
        for (e in this) {

            try {
                val notFilteredList = block(e)
                if (notFilteredList.isEmpty()) {
                    logger.info { "No data" }
                    return
                }

                var status = PROCESSED
                // дошли до конца данных, вернулся неполный батч

                if (notFilteredList.size < appConfig.paging.pageSize) {
                    val delta = appConfig.paging.pageSize.toLong() - notFilteredList.size
                    endIndex -= delta
                    status = PARTIALLY_PROCESSED
                }

                // пример исключения ошибочных данный и отправка в DLQ
                val errorsRecords = notFilteredList.filter { it.text == "To_DLQ" }

                notFilteredList.removeAll(errorsRecords)

                runBlocking {
                    errorsRecords.forEach { dlqProducer.sendData(it, Exception("Something wrong").message!!) }
                    notFilteredList.forEach { dataProducer.sendData(it) }
                    // метрики
                    if (notFilteredList.isNotEmpty()) counter.increment(notFilteredList.size.toDouble())
                    if (errorsRecords.isNotEmpty()) counterDlq.increment(errorsRecords.size.toDouble())

                    statusService.setStatus(endIndex, status)
                }
            } catch (e: Exception) {
                logger.error { "Postgresql error: ${e.message}" }
                runBlocking {
                    statusService.setStatus(endIndex, FAILED)
                }
            }
        }
    }
}
