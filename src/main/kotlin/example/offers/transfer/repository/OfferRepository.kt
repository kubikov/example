package example.repository

import org.jooq.DSLContext
import org.jooq.InsertValuesStep2
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import example.Tables.OUTPUT_TABLE
import example.domain.Output

class OfferRepository : KoinComponent {

    private val dsl by inject<DSLContext>()
    private val ot = OUTPUT_TABLE

    fun selectRangeByIndex(startId: Long, endId: Long) = Flux.from(
        dsl.selectFrom(ot).where(ot.ID.greaterOrEqual(startId))
            .and(ot.ID.lessOrEqual(endId))
    ).map {
        Output(it.get(ot.ID), it.get(ot.DATA_TEXT))
    }.collectList().block()
        ?: listOf()

    fun createTestData(startIndex: Int) {
        var exampleText: String

        for (i in 0..INSERT_COUNT) {

            val inserts: MutableList<InsertValuesStep2<org.jooq.Record, Long, String>> = mutableListOf()

            for (r in i * BATCH_SIZE + 1..i * BATCH_SIZE + BATCH_SIZE) {
                exampleText = if (r == 13) "To_DLQ" else "Synthetic_Data"
                inserts.add(
                    dsl
                        .insertInto(ot, ot.ID, ot.DATA_TEXT)
                        .values(r.toLong() + startIndex, exampleText)
                )
            }

            Mono.from(
                dsl.batch(
                    inserts
                )
            ).block() ?: throw Exception("Ошибка при добавлении")
        }
    }

    companion object {
        const val BATCH_SIZE = 17300
        const val INSERT_COUNT = 7
    }
}
