package example.service

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import example.config.AppConfig
import example.domain.Output
import example.kafka.dispatch

class DlqProducer(private val kafkaProducer: KafkaProducer<Long, Output>) : KoinComponent {

    private val appConfig by inject<AppConfig>()

    suspend fun sendData(data: Output, exception: String) {

        val headers = listOf(RecordHeader("exception", exception.toByteArray()))

        kafkaProducer.dispatch(
            ProducerRecord<Long, Output>(
                appConfig.kafka.consumer.dlqTopic,
                0,
                null,
                data,
                headers
            )
        )

        // logger.info { "Send data: $data" }
    }
}
