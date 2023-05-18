package example.domain

import java.time.Instant

data class PagingState(
    val id: Long,
    val timestamp: Instant,
    val message: String
)
