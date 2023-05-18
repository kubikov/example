package example.plugins

import io.ktor.server.application.*
import org.koin.ktor.ext.inject
import example.config.AppConfig
import example.domain.PagingState
import example.kafka.buildConsumer

fun Application.configureKafka() {

    val appConfig by inject<AppConfig>()

    install(BackgroundJob.BackgroundJobConsumerPaging) {
        name = "Consumer-Job"
        job = buildConsumer<Long, PagingState>(appConfig)
    }
}
