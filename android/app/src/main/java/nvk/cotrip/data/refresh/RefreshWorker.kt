package nvk.cotrip.data.refresh

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.repository.NotificationRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.data.sync.SyncPullRepository
import nvk.cotrip.notifications.SystemNotificationManager
import nvk.cotrip.util.AppLogger

@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessionStore: SessionStore,
    private val networkStateProvider: NetworkStateProvider,
    private val tripRepository: TripRepository,
    private val userRepository: UserRepository,
    private val syncPullRepository: SyncPullRepository,
    private val notificationRepository: NotificationRepository,
    private val systemNotificationManager: SystemNotificationManager,
) : CoroutineWorker(context, params) {
    private companion object {
        private const val TAG = "RefreshWorker"
    }

    override suspend fun doWork(): Result {
        if (!networkStateProvider.isOnline()) {
            AppLogger.i(TAG, "skip: offline, scheduling retry")
            return Result.retry()
        }

        if (!sessionStore.hasSession()) {
            AppLogger.i(TAG, "skip: no auth token")
            return Result.success()
        }

        AppLogger.i(TAG, "start pull cycle")
        val syncResult = syncPullRepository.pull()
        val tripsResult = tripRepository.refreshTrips()
        val meResult = userRepository.refreshMe()
        val notificationsRefreshResult = notificationRepository.refreshNotifications()
        val notificationsResult = runCatching { notificationRepository.notifications.first() }
        notificationsResult.getOrNull()?.let { items ->
            AppLogger.i(TAG, "notifications fetched: count=${items.size}")
            systemNotificationManager.syncWithServer(items)
        }
        notificationsResult.exceptionOrNull()?.let { error ->
            AppLogger.w(TAG, "notifications fetch failed", error)
        }

        return if (
            syncResult.isSuccess &&
            tripsResult.isSuccess &&
            meResult.isSuccess &&
            notificationsRefreshResult.isSuccess &&
            notificationsResult.isSuccess
        ) {
            AppLogger.i(TAG, "cycle completed successfully")
            Result.success()
        } else {
            AppLogger.w(TAG, "cycle failed, scheduling retry")
            Result.retry()
        }
    }
}
