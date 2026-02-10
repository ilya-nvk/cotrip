package nvk.cotrip.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nvk.cotrip.R
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.util.AppLogger

@Singleton
class SystemNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun syncWithServer(items: List<NotificationDto>) {
        ensureChannel()
        val delivered = deliveredIds().toMutableSet()
        val currentIds = items.map { it.id }.toSet()

        items.forEach { item ->
            val id = item.id
            if (item.readAt != null) {
                cancelById(id)
                delivered.remove(id)
                return@forEach
            }

            if (shouldSuppress(item)) {
                cancelById(id)
                delivered.remove(id)
                return@forEach
            }

            if (id !in delivered) {
                show(item)
                delivered.add(id)
            }
        }

        val stale = delivered.filter { it !in currentIds }
        stale.forEach { id ->
            cancelById(id)
            delivered.remove(id)
        }
        saveDelivered(delivered)
    }

    fun onMarkedRead(notificationId: String) {
        cancelById(notificationId)
        val delivered = deliveredIds().toMutableSet()
        delivered.remove(notificationId)
        saveDelivered(delivered)
    }

    private fun shouldSuppress(item: NotificationDto): Boolean {
        if (!AppRuntimeState.isAppForeground()) return false
        if (item.type != "idea_comment") return true
        val ideaId = payloadValue(item, "ideaId")
        return !ideaId.isNullOrBlank() && AppRuntimeState.isDiscussionOpenForIdea(ideaId)
    }

    private fun show(item: NotificationDto) {
        if (!hasNotificationPermission()) return
        val title = buildTitle(item)
        val body = buildBody(item)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(platformId(item.id), notification)
        }.onFailure { error ->
            AppLogger.w(TAG, "Failed to display local notification", error)
        }
    }

    private fun buildTitle(item: NotificationDto): String {
        return when (item.type) {
            "idea_comment" -> "New comment"
            "idea_created" -> "New idea"
            "expense_created" -> "New expense"
            "expense_settlement" -> "Expense settled"
            else -> "Update"
        }
    }

    private fun buildBody(item: NotificationDto): String {
        val actor = payloadValue(item, "actorName").orEmpty()
        val commentBody = payloadValue(item, "body").orEmpty()
        val ideaTitle = payloadValue(item, "ideaTitle").orEmpty()
        val expenseTitle = payloadValue(item, "title").orEmpty()
        return when (item.type) {
            "idea_comment" -> listOf(actor, commentBody).filter { it.isNotBlank() }.joinToString(": ")
                .ifBlank { "Someone commented in idea discussion" }
            "idea_created" -> listOf(actor, ideaTitle).filter { it.isNotBlank() }.joinToString(": ")
                .ifBlank { "New idea in your trip" }
            "expense_created" -> listOf(actor, expenseTitle).filter { it.isNotBlank() }.joinToString(": ")
                .ifBlank { "New expense in your trip" }
            "expense_settlement" -> listOf(actor, expenseTitle).filter { it.isNotBlank() }.joinToString(": ")
                .ifBlank { "An expense was settled" }
            else -> "You have a new update"
        }
    }

    private fun payloadValue(item: NotificationDto, key: String): String? {
        return runCatching {
            item.payload.jsonObject[key]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "CoTrip updates",
            NotificationManager.IMPORTANCE_HIGH
        )
        manager.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun cancelById(notificationId: String) {
        NotificationManagerCompat.from(context).cancel(platformId(notificationId))
    }

    private fun deliveredIds(): Set<String> {
        return prefs.getStringSet(KEY_DELIVERED_IDS, emptySet()).orEmpty()
    }

    private fun saveDelivered(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_DELIVERED_IDS, ids).apply()
    }

    private fun platformId(notificationId: String): Int {
        return notificationId.hashCode()
    }

    private companion object {
        private const val TAG = "SystemNotification"
        private const val CHANNEL_ID = "cotrip_updates"
        private const val PREFS_NAME = "cotrip_local_notifications"
        private const val KEY_DELIVERED_IDS = "delivered_ids"
    }
}
