package example.module

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.*
import org.koin.core.component.KoinComponent
import org.koin.dsl.module
import example.config.*

class HoconConfigHolder : KoinComponent {
    private val appConfig = HoconApplicationConfig(ConfigFactory.load())

    fun getConfig(): AppConfig {
        return AppConfig(
            Kafka(
                server = appConfig.propertyOrNull("ktor.kafka.bootstrap.servers")!!.getList(),
                consumer = Consumer(
                    pagingStateTopic = getProperty("ktor.kafka.consumer.paging-state-topic")!!,
                    statusTopic = getProperty("ktor.kafka.consumer.status-topic")!!,
                    dataTopic = getProperty("ktor.kafka.consumer.data-topic")!!,
                    dlqTopic = getProperty("ktor.kafka.consumer.dlq-topic")!!,
                    clientId = getProperty("ktor.kafka.consumer.client.id")!!,
                    groupId = getProperty("ktor.kafka.consumer.group.id")!!,
                ),
                producer = Producer(
                    getProperty("ktor.kafka.producer.client.id")!!,
                )
            ),
            PostgresConfig(
                url = "r2dbc:postgresql://${getProperty("ktor.jooq.pg.host")!!}:${
                getProperty("ktor.jooq.pg.port")!!}/${getProperty("ktor.jooq.pg.name")!!
                }",
                user = getProperty("ktor.jooq.pg.user")!!,
                password = getProperty("ktor.jooq.pg.password")!!,
            ),
            Paging(
                partitionLastIndex = getProperty("ktor.paging.partition_last_index")!!.toInt(),
                pageSize = getProperty("ktor.paging.page_size")!!.toInt(),
            )
        )
    }

    private fun getProperty(path: String, notNull: Boolean = false) =
        appConfig.propertyOrNull(path)?.getString()
            ?: if (notNull) throw Exception("Property not found: $path")
            else null
}

val configModule = module {
    single { HoconConfigHolder().getConfig() }
}
