package nvk.cotrip.notifications

import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.repository.PushTokenRepository
import nvk.cotrip.util.AppLogger

@Singleton
class PushTokenSyncManager @Inject constructor(
    private val sessionStore: SessionStore,
    private val pushTokenRepository: PushTokenRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun syncInBackground(tokenOverride: String? = null) {
        scope.launch {
            syncIfPossible(tokenOverride)
        }
    }

    suspend fun syncIfPossible(tokenOverride: String? = null) {
        if (sessionStore.getAccessToken().isNullOrBlank()) return
        val token = tokenOverride?.trim().orEmpty().ifBlank { fetchFirebaseToken() ?: return }
        runCatching {
            pushTokenRepository.upsert(token = token, platform = "android")
        }.onFailure { error ->
            AppLogger.w(TAG, "Failed to sync push token", error)
        }
    }

    suspend fun unregisterIfPossible() {
        if (sessionStore.getAccessToken().isNullOrBlank()) return
        val token = fetchFirebaseToken() ?: return
        runCatching {
            pushTokenRepository.delete(token)
        }.onFailure { error ->
            AppLogger.w(TAG, "Failed to unregister push token", error)
        }
    }

    private suspend fun fetchFirebaseToken(): String? {
        return runCatching {
            suspendCancellableCoroutine { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { token -> cont.resume(token) }
                    .addOnFailureListener { error -> cont.resumeWithException(error) }
            }
        }.onFailure { error ->
            AppLogger.w(TAG, "Failed to obtain FCM token", error)
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        private const val TAG = "PushTokenSync"
    }
}
