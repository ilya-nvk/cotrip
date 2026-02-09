package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
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

@Serializable
data class SyncChangeDto(
    val entity: String,
    val id: String,
    val updatedAt: String,
    val deletedAt: String? = null,
    val payload: JsonElement,
)

@Serializable
data class SyncPullResponse(
    val items: List<SyncChangeDto> = emptyList(),
    val nextCursor: String? = null,
)
