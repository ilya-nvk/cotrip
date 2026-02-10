package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val payload: JsonElement,
    val createdAt: String,
    val readAt: String? = null,
)

@Serializable
data class NotificationSettingDto(
    val key: String,
    val enabled: Boolean,
)

@Serializable
data class NotificationListResponse(
    val items: List<NotificationDto> = emptyList(),
    val nextCursor: String? = null,
)

@Serializable
data class NotificationSettingsResponse(
    val items: List<NotificationSettingDto> = emptyList(),
)

@Serializable
data class NotificationSettingsUpdateRequest(
    val items: List<NotificationSettingDto> = emptyList(),
)

@Serializable
data class UpsertPushTokenRequest(
    val token: String,
    val platform: String = "android",
)
