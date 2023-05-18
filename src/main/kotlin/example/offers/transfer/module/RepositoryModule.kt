package example.module

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactoryOptions
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.dsl.module
import example.config.AppConfig
import example.module.RepositoryModule.jooqBean
import example.repository.OfferRepository
import java.time.Duration

object RepositoryModule : KoinComponent {

    private val config by inject<AppConfig>()

    private fun r2dbConnection() = ConnectionFactories.get(
        ConnectionFactoryOptions
            .parse(config.postgres.url)
            .mutate()
            .option(ConnectionFactoryOptions.USER, config.postgres.user)
            .option(ConnectionFactoryOptions.PASSWORD, config.postgres.password)
            .build()
    )

    private fun connectionPool() = ConnectionPool(
        ConnectionPoolConfiguration.builder(r2dbConnection())
            .maxIdleTime(Duration.ofSeconds(10))
            .maxSize(3)
            .build()
    )

    fun jooqBean(): DSLContext = DSL.using(connectionPool())
}

val repositoryModule = module {
    single { jooqBean() }
    single { OfferRepository() }
}
