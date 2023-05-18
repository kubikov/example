package example.service

import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import example.config.AppConfig
import example.domain.PagingState
import example.domain.Status
import java.time.Instant

private val logger = KotlinLogging.logger {}

class StatusService : KoinComponent {

    private val statusProducer by inject<StatusProducer>()
    private val appConfig by inject<AppConfig>()

    fun isAllInstancesReturnedStatus(statuses: List<PagingState>, endIndex: Long): Boolean {

        val sortedStatuses = statuses.sortedBy { it.id }
        val firstStatusIndex = sortedStatuses.first().id
        val lastStatusIndex = sortedStatuses.last().id
        val allPartitions = appConfig.paging.partitionLastIndex + 1
        val indexFromFirstPartition = endIndex - ((allPartitions * appConfig.paging.pageSize) - appConfig.paging.pageSize)

        return if (lastStatusIndex == endIndex && firstStatusIndex == indexFromFirstPartition) {
            logger.info { "All instances return statuses: ${statuses.map { it.message }}" }
            true
        } else false
    }

    fun searchFailedStatuses(statuses: List<PagingState>) = statuses
        .filter { it.message == Status.FAILED.toString() }

    fun searchUnprocessedStatuses(statuses: List<PagingState>) = statuses
        .groupBy { it.id }
        .map { map ->
            if (map.value.size == 1) map.value.last() else null
        }.filterNotNull()

    fun searchPartiallyProcessedStatuses(statuses: List<PagingState>) = statuses
        .filter { it.message == Status.PARTIALLY_PROCESSED.toString() }

    suspend fun setStatus(endIndex: Long, status: Status) {
        statusProducer.sendStatus(
            PagingState(
                endIndex,
                Instant.now(),
                status.toString()
            )
        )

        logger.info { "Status: $status" }
    }
}
