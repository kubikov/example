package example.service

import mu.KotlinLogging
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import example.config.AppConfig
import example.domain.PagingState
import example.kafka.dispatch
import java.time.Instant

private val logger = KotlinLogging.logger {}

class PagingStateProducer(private val kafkaProducer: KafkaProducer<Long, PagingState>) : KoinComponent {

    private val appConfig by inject<AppConfig>()

    suspend fun sendPagingState(partition: Int, endIndex: Long) {

        val nextStartIndex = endIndex + 1
        kafkaProducer.dispatch(
            ProducerRecord<Long, PagingState>(
                appConfig.kafka.consumer.pagingStateTopic,
                partition,
                null,
                PagingState(nextStartIndex, Instant.now(), "Some text")
            )
        )
        logger.info { "Send state: id=$nextStartIndex, partition=$partition" }
    }
}
