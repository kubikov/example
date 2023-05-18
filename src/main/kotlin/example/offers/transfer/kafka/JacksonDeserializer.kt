package example.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.Deserializer
import example.domain.PagingState

class JacksonDeserializer(
    private val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerModule(KotlinModule())
) : Deserializer<PagingState?> {

    override fun deserialize(topic: String?, data: ByteArray?): PagingState? {
        try {
            if (data == null) {
                return null
            }
            return objectMapper.readValue(data, PagingState::class.java)
        } catch (e: Exception) {
            throw SerializationException("Error deserializing JSON message", e)
        }
    }
}
