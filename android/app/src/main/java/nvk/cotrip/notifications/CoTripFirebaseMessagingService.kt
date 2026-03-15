package nvk.cotrip.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.util.AppLogger
import java.time.Instant
import javax.inject.Inject

@AndroidEntryPoint
class CoTripFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var pushTokenSyncManager: PushTokenSyncManager

    @Inject
    lateinit var systemNotificationManager: SystemNotificationManager

    @Inject
    lateinit var json: Json

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            pushTokenSyncManager.onNewToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        if (data.isEmpty()) return
        val notification = parseNotification(data) ?: return
        systemNotificationManager.onPushNotification(notification)
    }

    private fun parseNotification(data: Map<String, String>): NotificationDto? {
        val id = data["notificationId"]?.trim().orEmpty()
        val type = data["type"]?.trim().orEmpty()
        if (id.isBlank() || type.isBlank()) {
            AppLogger.w(TAG, "skip push message: missing notificationId/type")
            return null
        }
        val payloadRaw = data["payload"].orEmpty().ifBlank { "{}" }
        val payload = runCatching { json.parseToJsonElement(payloadRaw) }
            .onFailure { error ->
                AppLogger.w(TAG, "invalid notification payload json", error)
            }
            .getOrNull() ?: return null
        val createdAt = data["createdAt"]?.trim().takeUnless { it.isNullOrBlank() }
            ?: Instant.now().toString()
        return NotificationDto(
            id = id,
            type = type,
            payload = payload,
            createdAt = createdAt,
            readAt = null,
        )
    }

    private companion object {
        private const val TAG = "FCMService"
    }
}
