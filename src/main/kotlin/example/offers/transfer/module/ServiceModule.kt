package example.module

import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.module
import example.config.AppConfig
import example.domain.PagingState
import example.kafka.buildProducer
import example.module.ServiceModule.dataProducer
import example.module.ServiceModule.dlqProducer
import example.module.ServiceModule.metric
import example.module.ServiceModule.pagingStateProducer
import example.module.ServiceModule.statusProducer
import example.service.*

object ServiceModule : KoinComponent {

    private val config by inject<AppConfig>()

    fun pagingStateProducer() = PagingStateProducer(buildProducer(config))
    fun statusProducer() = StatusProducer(buildProducer(config))
    fun dataProducer() = DataProducer(buildProducer(config))
    fun dlqProducer() = DlqProducer(buildProducer(config))
    fun metric() = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
}

val serviceModule = module {
    single { pagingStateProducer() }
    single { statusProducer() }
    single { dataProducer() }
    single { dlqProducer() }
    single<ConsumerService<PagingState>> { PagingService() }
    single { StatusService() }
    single { metric() }
}
