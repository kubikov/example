package example.kafka

import kotlinx.coroutines.suspendCancellableCoroutine
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.serialization.LongSerializer
import example.config.AppConfig
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun <K, V> buildProducer(appConfig: AppConfig): KafkaProducer<K, V> {

    val producerProps = Properties().apply {
        this[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = appConfig.kafka.server
        this[ProducerConfig.CLIENT_ID_CONFIG] = UUID.randomUUID().toString()
        this[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = LongSerializer::class.java
        this[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = JacksonSerializer::class.java
        this[ProducerConfig.PARTITIONER_CLASS_CONFIG] = RoundRobinPartitioner::class.java
    }
    return KafkaProducer(producerProps)
}

suspend inline fun <reified K : Any, reified V : Any> KafkaProducer<K, V>.dispatch(record: ProducerRecord<K, V>) =
    suspendCancellableCoroutine<RecordMetadata> { continuation ->
        this.send(record) { metadata, exception ->
            if (metadata == null) continuation.resumeWithException(exception!!) else continuation.resume(metadata)
        }
    }
