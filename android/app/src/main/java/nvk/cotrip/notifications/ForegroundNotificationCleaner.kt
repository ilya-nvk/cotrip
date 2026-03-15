package nvk.cotrip.notifications

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.repository.NotificationRepository
import nvk.cotrip.util.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundNotificationCleaner @Inject constructor(
    private val sessionStore: SessionStore,
    private val notificationRepository: NotificationRepository,
    private val systemNotificationManager: SystemNotificationManager,
) {
    private val runMutex = Mutex()

    suspend fun onAppForeground() {
        runMutex.withLock {
            if (!sessionStore.hasSession()) return
            notificationRepository.refreshNotifications()
                .onFailure { error ->
                    AppLogger.w(TAG, "refreshNotifications failed during foreground cleanup", error)
                }
            val items = runCatching { notificationRepository.notifications.first() }
                .onFailure { error ->
                    AppLogger.w(
                        TAG,
                        "read notifications flow failed during foreground cleanup",
                        error
                    )
                }
                .getOrDefault(emptyList())
            val idsToMark = items
                .asSequence()
                .filter { it.readAt == null && it.type != IDEA_COMMENT_TYPE }
                .map { it.id }
                .toList()
            if (idsToMark.isEmpty()) return

            notificationRepository.markReadBulkNonComment()
                .onFailure { error ->
                    AppLogger.w(TAG, "markReadBulkNonComment failed", error)
                }

            idsToMark.forEach { notificationId ->
                systemNotificationManager.onMarkedRead(notificationId)
            }
        }
    }

    private companion object {
        private const val TAG = "NotifFgCleaner"
        private const val IDEA_COMMENT_TYPE = "idea_comment"
    }
}
