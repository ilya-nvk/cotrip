package nvk.cotrip.notifications

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.repository.NotificationRepository
import nvk.cotrip.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushTokenSyncManager @Inject constructor(
    @ApplicationContext context: Context,
    private val sessionStore: SessionStore,
    private val notificationRepository: NotificationRepository,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun syncCurrentToken() {
        val token = fetchCurrentToken() ?: return
        rememberToken(token)
        if (!sessionStore.hasSession()) return
        notificationRepository.upsertPushToken(token)
            .onFailure { error ->
                AppLogger.w(TAG, "push token upsert failed", error)
            }
    }

    suspend fun onNewToken(token: String) {
        val normalized = token.trim()
        if (normalized.isBlank()) return
        rememberToken(normalized)
        if (!sessionStore.hasSession()) return
        notificationRepository.upsertPushToken(normalized)
            .onFailure { error ->
                AppLogger.w(TAG, "push token upsert failed for refreshed token", error)
            }
    }

    suspend fun unregisterRememberedToken() {
        val token = rememberedToken() ?: return
        if (!sessionStore.hasSession()) return
        notificationRepository.deletePushToken(token)
            .onSuccess {
                clearRememberedToken()
            }
            .onFailure { error ->
                AppLogger.w(TAG, "push token delete failed", error)
            }
    }

    private suspend fun fetchCurrentToken(): String? {
        return runCatching {
            FirebaseMessaging.getInstance().token.await()
        }.onFailure { error ->
            AppLogger.w(TAG, "failed to fetch current FCM token", error)
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun rememberedToken(): String? {
        return prefs.getString(KEY_LAST_TOKEN, null)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun rememberToken(token: String) {
        prefs.edit().putString(KEY_LAST_TOKEN, token).apply()
    }

    private fun clearRememberedToken() {
        prefs.edit().remove(KEY_LAST_TOKEN).apply()
    }

    private companion object {
        private const val TAG = "PushTokenSync"
        private const val PREFS_NAME = "cotrip_push_tokens"
        private const val KEY_LAST_TOKEN = "last_token"
    }
}
