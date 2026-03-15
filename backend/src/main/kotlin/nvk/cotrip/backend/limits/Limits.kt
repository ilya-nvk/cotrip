package nvk.cotrip.backend.limits

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate
import java.time.OffsetDateTime

object Limits {
    const val TRIPS_PER_OWNER = 100
    const val IDEAS_PER_TRIP = 300
    const val EXPENSES_PER_TRIP = 500
    const val ACTIVITIES_PER_DAY = 40
    const val COMMENTS_PER_IDEA = 1000
}

data class OldestCandidate(
    val id: String,
    val label: String?,
    val createdAt: OffsetDateTime? = null,
    val startDate: LocalDate? = null,
    val deletable: Boolean,
)

class LimitReachedException(
    val entity: String,
    val scopeId: String,
    val limit: Int,
    val currentCount: Int,
    val oldestCandidate: OldestCandidate?,
) : RuntimeException("Limit reached for entity=$entity")

fun LimitReachedException.toDetailsJson(): JsonObject {
    return buildJsonObject {
        put("entity", JsonPrimitive(entity))
        put("scopeId", JsonPrimitive(scopeId))
        put("limit", JsonPrimitive(limit))
        put("currentCount", JsonPrimitive(currentCount))
        put(
            "oldestCandidate",
            oldestCandidate?.let { candidate ->
                buildJsonObject {
                    put("id", JsonPrimitive(candidate.id))
                    put("label", candidate.label?.let(::JsonPrimitive) ?: JsonNull)
                    put("createdAt", candidate.createdAt?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                    put("startDate", candidate.startDate?.toString()?.let(::JsonPrimitive) ?: JsonNull)
                    put("deletable", JsonPrimitive(candidate.deletable))
                }
            } ?: JsonNull
        )
    }
}
