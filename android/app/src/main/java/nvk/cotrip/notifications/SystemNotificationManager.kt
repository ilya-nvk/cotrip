package nvk.cotrip.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nvk.cotrip.MainActivity
import nvk.cotrip.R
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.util.AppLogger
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun syncWithServer(items: List<NotificationDto>) {
        ensureChannel()
        val delivered = deliveredIds().toMutableSet()
        val currentIds = items.map { it.id }.toSet()
        AppLogger.i(TAG, "sync start: server=${items.size}, delivered=${delivered.size}")

        items.forEach { item ->
            val id = item.id
            if (item.readAt != null) {
                cancelById(id)
                delivered.remove(id)
                return@forEach
            }

            if (shouldSuppress(item)) {
                AppLogger.i(TAG, "suppressed in foreground: id=$id type=${item.type}")
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

    fun onPushNotification(item: NotificationDto) {
        ensureChannel()
        val delivered = deliveredIds().toMutableSet()
        val id = item.id

        if (item.readAt != null) {
            cancelById(id)
            delivered.remove(id)
            saveDelivered(delivered)
            return
        }

        if (shouldSuppress(item)) {
            AppLogger.i(TAG, "suppressed push in foreground: id=$id type=${item.type}")
            cancelById(id)
            delivered.remove(id)
            saveDelivered(delivered)
            return
        }

        if (id !in delivered) {
            show(item)
            delivered.add(id)
            saveDelivered(delivered)
        }
    }

    fun onMarkedRead(notificationId: String) {
        cancelById(notificationId)
        val delivered = deliveredIds().toMutableSet()
        delivered.remove(notificationId)
        saveDelivered(delivered)
    }

    fun onIdeaDiscussionRead(ideaId: String) {
        prefs.edit().putLong(keyIdeaLastRead(ideaId), System.currentTimeMillis()).apply()
        AppLogger.i(TAG, "idea discussion read: ideaId=$ideaId")
    }

    private fun shouldSuppress(item: NotificationDto): Boolean {
        if (item.type == "idea_comment") {
            val ideaId = payloadValue(item, "ideaId")
            if (!ideaId.isNullOrBlank()) {
                val createdAtMillis = parseCreatedAtMillis(item.createdAt)
                val seenAtMillis = prefs.getLong(keyIdeaLastRead(ideaId), 0L)
                if (createdAtMillis != null && seenAtMillis > 0L && createdAtMillis <= seenAtMillis) {
                    AppLogger.i(
                        TAG,
                        "suppressed already-read comment: id=${item.id}, ideaId=$ideaId"
                    )
                    return true
                }
            }
        }
        if (!AppRuntimeState.isAppForeground()) return false
        if (item.type != "idea_comment") return true
        val ideaId = payloadValue(item, "ideaId")
        return !ideaId.isNullOrBlank() && AppRuntimeState.isDiscussionOpenForIdea(ideaId)
    }

    private fun show(item: NotificationDto) {
        if (!hasNotificationPermission()) {
            AppLogger.w(TAG, "skip show: notifications permission denied")
            return
        }
        val title = buildTitle(item)
        val body = buildBody(item)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(buildContentIntent(item))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(platformId(item.id), notification)
            AppLogger.i(TAG, "shown: id=${item.id} type=${item.type}")
        }.onFailure { error ->
            AppLogger.w(TAG, "Failed to display local notification", error)
        }
    }

    private fun buildTitle(item: NotificationDto): String {
        return when (item.type) {
            "idea_comment" -> context.getString(R.string.notification_title_new_comment)
            "idea_created" -> context.getString(R.string.notification_title_new_idea)
            "expense_created" -> context.getString(R.string.notification_title_new_expense)
            "expense_settlement" -> context.getString(R.string.notification_title_expense_settled)
            else -> context.getString(R.string.notification_title_update)
        }
    }

    private fun buildBody(item: NotificationDto): String {
        val actor = payloadValue(item, "actorName").orEmpty()
        val commentBody = payloadValue(item, "body").orEmpty()
        val ideaTitle = payloadValue(item, "ideaTitle").orEmpty()
        val expenseTitle = payloadValue(item, "title").orEmpty()
        return when (item.type) {
            "idea_comment" -> formatActorValue(actor, commentBody)
                .ifBlank { context.getString(R.string.notification_body_commented_fallback) }

            "idea_created" -> formatActorValue(actor, ideaTitle)
                .ifBlank { context.getString(R.string.notification_body_new_idea_fallback) }

            "expense_created" -> formatActorValue(actor, expenseTitle)
                .ifBlank { context.getString(R.string.notification_body_new_expense_fallback) }

            "expense_settlement" -> formatActorValue(actor, expenseTitle)
                .ifBlank { context.getString(R.string.notification_body_expense_settled_fallback) }

            else -> context.getString(R.string.notification_body_update_fallback)
        }
    }

    private fun formatActorValue(actor: String, value: String): String {
        return when {
            actor.isNotBlank() && value.isNotBlank() ->
                context.getString(R.string.notification_body_actor_value, actor, value)

            actor.isNotBlank() -> actor
            value.isNotBlank() -> value
            else -> ""
        }
    }

    private fun payloadValue(item: NotificationDto, key: String): String? {
        return runCatching {
            item.payload.jsonObject[key]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private fun payloadValueAny(item: NotificationDto, vararg keys: String): String? {
        keys.forEach { key ->
            val value = payloadValue(item, key)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun parseCreatedAtMillis(raw: String): Long? {
        return try {
            Instant.parse(raw).toEpochMilli()
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private fun buildContentIntent(item: NotificationDto): PendingIntent {
        val deepLinkUri = buildDeepLinkUri(item)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = deepLinkUri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationIntentExtras.EXTRA_NOTIFICATION_ID, item.id)
            putExtra(NotificationIntentExtras.EXTRA_EVENT, item.type)
            payloadValueAny(item, "tripId", "trip_id", "tripID")?.let {
                putExtra(
                    NotificationIntentExtras.EXTRA_TRIP_ID,
                    it
                )
            }
            payloadValueAny(item, "ideaId", "idea_id", "ideaID")?.let {
                putExtra(
                    NotificationIntentExtras.EXTRA_IDEA_ID,
                    it
                )
            }
            payloadValueAny(item, "expenseId", "expense_id", "expenseID")
                ?.let { putExtra(NotificationIntentExtras.EXTRA_EXPENSE_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            platformId(item.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildDeepLinkUri(item: NotificationDto): Uri {
        val tripId = payloadValueAny(item, "tripId", "trip_id", "tripID")
        val ideaId = payloadValueAny(item, "ideaId", "idea_id", "ideaID")
        val expenseId = payloadValueAny(item, "expenseId", "expense_id", "expenseID")
        val url = when (item.type) {
            "idea_comment", "idea_created" -> {
                if (!tripId.isNullOrBlank() && !ideaId.isNullOrBlank()) {
                    "https://api.cotrip.site/trips/$tripId/ideas/$ideaId"
                } else {
                    "https://api.cotrip.site/notifications"
                }
            }

            "expense_created", "expense_settlement" -> {
                when {
                    !tripId.isNullOrBlank() && !expenseId.isNullOrBlank() ->
                        "https://api.cotrip.site/trips/$tripId/expenses/$expenseId"

                    !tripId.isNullOrBlank() ->
                        "https://api.cotrip.site/trips/$tripId/expenses"

                    else -> "https://api.cotrip.site/notifications"
                }
            }

            else -> "https://api.cotrip.site/notifications"
        }
        return Uri.parse(url)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_updates),
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
        private const val KEY_IDEA_LAST_READ_PREFIX = "idea_last_read_"

        private fun keyIdeaLastRead(ideaId: String): String = "$KEY_IDEA_LAST_READ_PREFIX$ideaId"
    }
}
