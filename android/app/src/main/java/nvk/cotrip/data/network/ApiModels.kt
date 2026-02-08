package nvk.cotrip.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ApiErrorResponse(
    val error: ApiError,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val details: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class ApiListResponse<T>(
    val items: List<T> = emptyList(),
    @SerialName("nextCursor") val nextCursor: String? = null,
)
