package nvk.cotrip.notifications

import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.dto.NotificationDto
import java.time.Instant
import javax.inject.Inject

/**
 * Parses FCM data map into [NotificationDto].
 * Used by [CoTripFirebaseMessagingService] for testability.
 */
class NotificationPayloadParser @Inject constructor(
    private val json: Json,
) {

    fun parse(
        data: Map<String, String>,
        defaultCreatedAt: String = Instant.now().toString(),
    ): NotificationDto? {
        val id = data["notificationId"]?.trim().orEmpty()
        val type = data["type"]?.trim().orEmpty()
        if (id.isBlank() || type.isBlank()) return null
        val payloadRaw = data["payload"].orEmpty().ifBlank { "{}" }
        val payload = runCatching { json.parseToJsonElement(payloadRaw) }.getOrNull() ?: return null
        val createdAt = data["createdAt"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: defaultCreatedAt
        return NotificationDto(
            id = id,
            type = type,
            payload = payload,
            createdAt = createdAt,
            readAt = null,
        )
    }
}
