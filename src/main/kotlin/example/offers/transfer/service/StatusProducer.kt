package example.service

import mu.KotlinLogging
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import example.config.AppConfig
import example.domain.PagingState
import example.kafka.dispatch

private val logger = KotlinLogging.logger {}

class StatusProducer(private val kafkaProducer: KafkaProducer<Long, PagingState>) : KoinComponent {

    private val appConfig by inject<AppConfig>()

    suspend fun sendStatus(status: PagingState) {
        kafkaProducer.dispatch(
            ProducerRecord<Long, PagingState>(
                appConfig.kafka.consumer.statusTopic,
                0,
                null,
                status
            )
        )
        logger.info { "Send status: id=${status.id}" }
    }
}
