package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SyncChangeItem(
    val entity: String,
    val id: String,
    val type: String,
    val payload: JsonObject? = null,
)

@Serializable
data class SyncChangesRequest(
    val items: List<SyncChangeItem>,
)

@Serializable
data class SyncConflict(
    val id: String,
    val reason: String,
)

@Serializable
data class SyncChangesResponse(
    val applied: List<String> = emptyList(),
    val conflicts: List<SyncConflict> = emptyList(),
)
