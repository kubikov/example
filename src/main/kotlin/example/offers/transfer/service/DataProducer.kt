package example.service

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import example.config.AppConfig
import example.domain.Output
import example.kafka.dispatch

class DataProducer(private val kafkaProducer: KafkaProducer<Long, Output>) : KoinComponent {

    private val appConfig by inject<AppConfig>()

    suspend fun sendData(data: Output) {
        kafkaProducer.dispatch(
            ProducerRecord<Long, Output>(
                appConfig.kafka.consumer.dataTopic,
                0,
                null,
                data
            )
        )
        // logger.info { "Send data: $data" }
    }
}
