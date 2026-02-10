package nvk.cotrip.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import nvk.cotrip.R
import nvk.cotrip.util.AppLogger

@AndroidEntryPoint
class CoTripFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var pushTokenSyncManager: PushTokenSyncManager

    override fun onNewToken(token: String) {
        pushTokenSyncManager.syncInBackground(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        ensureNotificationChannel()

        val title = message.notification?.title
            ?: message.data["title"]
            ?: getString(R.string.app_name)
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "You have a new update"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(this)
                .notify(System.currentTimeMillis().toInt(), notification)
        }.onFailure { error ->
            AppLogger.w(TAG, "Failed to show push notification", error)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "CoTrip updates",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    private companion object {
        private const val TAG = "CoTripFcmService"
        const val CHANNEL_ID = "cotrip_updates"
    }
}
