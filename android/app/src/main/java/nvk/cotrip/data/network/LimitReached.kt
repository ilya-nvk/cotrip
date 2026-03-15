package nvk.cotrip.data.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class LimitOldestCandidate(
    val id: String,
    val label: String?,
    val createdAt: String?,
    val startDate: String?,
    val deletable: Boolean,
)

data class LimitReachedDetails(
    val entity: String,
    val scopeId: String,
    val limit: Int,
    val currentCount: Int,
    val oldestCandidate: LimitOldestCandidate?,
)

fun ApiResult.Failure.limitReachedDetails(): LimitReachedDetails? {
    if (error?.code != "limit_reached") return null
    return parseLimitReachedDetails(error?.details)
}

fun parseLimitReachedDetails(details: JsonObject?): LimitReachedDetails? {
    if (details == null) return null
    val entity = details.stringValue("entity") ?: return null
    val scopeId = details.stringValue("scopeId") ?: return null
    val limit = details["limit"]?.jsonPrimitive?.intOrNull ?: return null
    val currentCount = details["currentCount"]?.jsonPrimitive?.intOrNull ?: return null
    val oldestNode = details["oldestCandidate"]
    val oldest = oldestNode?.let {
        val obj = runCatching { it.jsonObject }.getOrNull() ?: return@let null
        val id = obj.stringValue("id") ?: return@let null
        LimitOldestCandidate(
            id = id,
            label = obj.stringValue("label"),
            createdAt = obj.stringValue("createdAt"),
            startDate = obj.stringValue("startDate"),
            deletable = obj["deletable"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }
    return LimitReachedDetails(
        entity = entity,
        scopeId = scopeId,
        limit = limit,
        currentCount = currentCount,
        oldestCandidate = oldest,
    )
}

private fun JsonObject.stringValue(key: String): String? {
    val value = this[key] ?: return null
    return runCatching { value.jsonPrimitive.content }.getOrNull()
}
