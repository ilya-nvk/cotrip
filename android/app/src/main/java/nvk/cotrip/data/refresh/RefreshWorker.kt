package nvk.cotrip.data.refresh

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.repository.NotificationRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.data.sync.SyncPullRepository
import nvk.cotrip.notifications.SystemNotificationManager

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
    override suspend fun doWork(): Result {
        if (!networkStateProvider.isOnline()) {
            return Result.retry()
        }

        val token = sessionStore.getAccessToken().orEmpty()
        if (token.isBlank()) {
            return Result.success()
        }

        val syncResult = syncPullRepository.pull()
        val tripsResult = tripRepository.refreshTrips()
        val meResult = userRepository.refreshMe()
        val notificationsResult = runCatching { notificationRepository.listNotifications() }
        notificationsResult.getOrNull()?.let { items ->
            systemNotificationManager.syncWithServer(items)
        }

        return if (
            syncResult.isSuccess &&
            tripsResult.isSuccess &&
            meResult.isSuccess &&
            notificationsResult.isSuccess
        ) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
