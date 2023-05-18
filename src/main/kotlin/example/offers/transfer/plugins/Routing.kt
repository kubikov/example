package example.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import example.domain.PagingState
import example.domain.Status
import example.repository.OfferRepository
import example.service.PagingStateProducer
import example.service.StatusProducer
import java.time.Instant

fun Application.configureRouting() {

    val pagingStateProducer by inject<PagingStateProducer>()
    val offerRepository by inject<OfferRepository>()
    val statusProducer by inject<StatusProducer>()

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }

    routing {
        get("/") {
            call.respond(HttpStatusCode.OK)
        }

        post("/kafka/paging") {

            pagingStateProducer.sendPagingState(0, 0)
            call.respond(HttpStatusCode.Accepted)
        }

        get("/start") {
            statusProducer.sendStatus(PagingState(-1, Instant.now(), Status.BEGIN.toString()))
            call.respond(HttpStatusCode.Accepted, Status.BEGIN)
        }

        post("/postgres/insert_all/{start_index}") {
            val startIndex = call.parameters["start_index"]!!.toInt()
            offerRepository.createTestData(startIndex)
            call.respond(HttpStatusCode.Accepted)
        }
    }
}
