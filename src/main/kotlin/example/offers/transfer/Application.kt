package example

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import org.koin.ktor.plugin.Koin
import example.module.configModule
import example.module.repositoryModule
import example.module.serviceModule
import example.plugins.configureKafka
import example.plugins.configureMonitoring
import example.plugins.configureRouting
import example.plugins.configureSerialization

fun main(args: Array<String>) {
    EngineMain.main(args)
}

@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {

    install(Koin) {
        modules(
            listOf(
                configModule, serviceModule, repositoryModule
            )
        )
    }
    configureMonitoring()
    configureSerialization()
    configureRouting()
    configureKafka()
}
