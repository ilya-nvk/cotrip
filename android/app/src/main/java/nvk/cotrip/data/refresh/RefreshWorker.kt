package nvk.cotrip.data.refresh

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
import java.util.concurrent.TimeUnit

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
        private const val UNIQUE_BACKGROUND_POLL_WORK = "refresh-background-poll"
        private const val BACKGROUND_POLL_INTERVAL_MINUTES = 2L
    }

    override suspend fun doWork(): Result {
        if (!networkStateProvider.isOnline()) {
            AppLogger.i(TAG, "skip: offline, scheduling retry")
            return Result.retry()
        }

        val token = sessionStore.getAccessToken().orEmpty()
        if (token.isBlank()) {
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
        scheduleNextBackgroundPoll()

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

    private fun scheduleNextBackgroundPoll() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInitialDelay(BACKGROUND_POLL_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            UNIQUE_BACKGROUND_POLL_WORK,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
