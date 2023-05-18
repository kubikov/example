package example.kafka

import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.LongDeserializer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import example.config.AppConfig
import example.plugins.ClosableJob
import example.service.ConsumerService
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val logger = KotlinLogging.logger {}

class Consumer<K, V>(private val consumer: KafkaConsumer<K, V>) : ClosableJob, KoinComponent {

    private val closed: AtomicBoolean = AtomicBoolean(false)
    private var finished = CountDownLatch(1)
    private val consumerService by inject<ConsumerService<V>>()
    private val appConfig by inject<AppConfig>()
    private val statusTopic = appConfig.kafka.consumer.statusTopic
    private val pagingStateTopic = appConfig.kafka.consumer.pagingStateTopic
    private var statusSeekedOffset: Long? = null
    private var subscribedStatusTopic = false

    init {
        consumer.subscribe(listOf(pagingStateTopic))
    }

    override fun run() {
        try {

            while (!closed.get()) {

                subscribedStatus()

                val records = consumer.poll(Duration.of(1000, ChronoUnit.MILLIS))

                // TODO add debug mode
                /*records.forEach {
                    logger.info { "topic = ${it.topic()}, partition = ${it.partition()}, offset = ${it.offset()}, key = ${it.key()}, value = ${it.value()}" }
                }*/

                val statusRecords = records.filter { it.topic() == statusTopic }
                val pagingStateRecords = records.filter { it.topic() == pagingStateTopic }
                val topicPartitions = consumer.assignment().filter { it.topic() == pagingStateTopic }

                if (statusRecords.isNotEmpty()) consumerService.statuses(
                    statusRecords.map { it.value() },
                    topicPartitions
                )

                val statusPartition = consumer.assignment().filter { it.topic() == statusTopic }

                consumer.endOffsets(statusPartition).forEach { (topicPartition: Any?, offset: Any) ->
                    var statusNewOffset = offset - (appConfig.paging.partitionLastIndex + 1) * 2
                    if (statusNewOffset < 0) statusNewOffset = 0

                    if (offset != statusSeekedOffset) {
                        consumer.seek(topicPartition, statusNewOffset)
                        statusSeekedOffset = statusNewOffset
                    }
                }

                if (pagingStateRecords.isNotEmpty()) {

                    consumerService.processRecords(
                        pagingStateRecords.map { it.value() },
                        pagingStateRecords.last().partition()
                    )

                    commit()
                }
            }
            logger.info { "Finish consuming" }
        } catch (e: Throwable) {
            when (e) {
                is WakeupException -> logger.info { "Consumer waked up" }
                else -> logger.error(e) { "Polling failed" }
            }
        } finally {
            logger.info { "Commit offset synchronously" }
            consumer.commitSync()
            consumer.close()
            finished.countDown()
            logger.info { "Consumer successfully closed" }
        }
    }

    private fun subscribedStatus() {
        val pagingStatePartitions = consumer.assignment()
        val lastPartitionEnabled = pagingStatePartitions.any { it.partition() == appConfig.paging.partitionLastIndex }

        // если это инстанс висит на последней партиции, то подписываем его еще и на чтение статусов
        if (lastPartitionEnabled && !subscribedStatusTopic) {
            consumer.subscribe(listOf(pagingStateTopic, statusTopic))
            subscribedStatusTopic = true
            logger.info { "SUBSCRIBED STATUS ON LAST PARTITION INSTANCE" }
        }

        if (pagingStatePartitions.isNotEmpty() && !lastPartitionEnabled && subscribedStatusTopic) {
            consumer.subscribe(listOf(pagingStateTopic))
            subscribedStatusTopic = false
            logger.info { "UNSUBSCRIBED STATUS" }
        }
    }

    override fun close() {
        logger.info { "Close job..." }
        closed.set(true)
        consumer.wakeup()
        finished.await(3000, TimeUnit.MILLISECONDS)
        logger.info { "Job is successfully closed" }
    }

    private fun commit(offset: Map<TopicPartition, OffsetAndMetadata>? = null) {

        offset?.let { map ->
            consumer.commitAsync(map) { offsets, exception ->
                exception?.let {
                    logger.error(it) { "Commit failed for offsets $offsets" }
                } ?: logger.info { "Offset committed  $offsets" }
            }
        } ?: consumer.commitAsync { offsets, exception ->
            exception?.let {
                logger.error(it) { "Commit failed for offsets $offsets" }
            } ?: logger.info { "Status offset committed  $offsets" }
        }
    }
}

fun <K, V> buildConsumer(appConfig: AppConfig): Consumer<K, V> {

    val consumerProps = Properties().apply {
        this[ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG] = appConfig.kafka.server
        this[ConsumerConfig.CLIENT_ID_CONFIG] = appConfig.kafka.consumer.clientId
        this[ConsumerConfig.GROUP_ID_CONFIG] = appConfig.kafka.consumer.groupId
        this[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] = false
        this[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = LongDeserializer::class.java
        this[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = JacksonDeserializer::class.java
    }
    return Consumer(KafkaConsumer(consumerProps))
}
