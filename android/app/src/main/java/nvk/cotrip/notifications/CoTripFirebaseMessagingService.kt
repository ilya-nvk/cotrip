package nvk.cotrip.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CoTripFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var pushTokenSyncManager: PushTokenSyncManager

    @Inject
    lateinit var systemNotificationManager: SystemNotificationManager

    @Inject
    lateinit var notificationPayloadParser: NotificationPayloadParser

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
        val notification = notificationPayloadParser.parse(data) ?: return
        systemNotificationManager.onPushNotification(notification)
    }
}
