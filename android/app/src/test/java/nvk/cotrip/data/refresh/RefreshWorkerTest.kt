package nvk.cotrip.data.refresh

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.data.repository.NotificationRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.data.sync.SyncPullRepository
import nvk.cotrip.notifications.SystemNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RefreshWorkerTest {

    @Test
    fun given_offline_when_doWork_then_returnsRetry() = runTest {
        // GIVEN
        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns false
        val worker = createWorker(networkStateProvider = networkStateProvider)

        // WHEN
        val result = worker.doWork()

        // THEN
        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
    }

    @Test
    fun given_noSession_when_doWork_then_returnsSuccess() = runTest {
        // GIVEN
        val sessionStore = mockk<SessionStore>(relaxed = true)
        every { sessionStore.hasSession() } returns false
        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns true
        val worker = createWorker(
            sessionStore = sessionStore,
            networkStateProvider = networkStateProvider,
        )

        // WHEN
        val result = worker.doWork()

        // THEN
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
    }

    @Test
    fun given_onlineWithSessionAndAllReposSuccess_when_doWork_then_returnsSuccess() = runTest {
        // GIVEN
        val sessionStore = mockk<SessionStore>(relaxed = true)
        every { sessionStore.hasSession() } returns true
        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns true
        val syncPullRepository = mockk<SyncPullRepository>()
        coEvery { syncPullRepository.pull() } returns Result.success(Unit)
        val tripRepository = mockk<TripRepository>()
        coEvery { tripRepository.refreshTrips() } returns Result.success(Unit)
        val userRepository = mockk<UserRepository>()
        coEvery { userRepository.refreshMe() } returns Result.success(Unit)
        val notificationRepository = mockk<NotificationRepository>()
        coEvery { notificationRepository.refreshNotifications() } returns Result.success(Unit)
        coEvery { notificationRepository.notifications } returns flowOf(emptyList<NotificationDto>())
        val systemNotificationManager = mockk<SystemNotificationManager>(relaxed = true)
        val worker = createWorker(
            sessionStore = sessionStore,
            networkStateProvider = networkStateProvider,
            tripRepository = tripRepository,
            userRepository = userRepository,
            syncPullRepository = syncPullRepository,
            notificationRepository = notificationRepository,
            systemNotificationManager = systemNotificationManager,
        )

        // WHEN
        val result = worker.doWork()

        // THEN
        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
    }

    @Test
    fun given_syncPullFails_when_doWork_then_returnsRetry() = runTest {
        // GIVEN
        val sessionStore = mockk<SessionStore>(relaxed = true)
        every { sessionStore.hasSession() } returns true
        val networkStateProvider = mockk<NetworkStateProvider>()
        every { networkStateProvider.isOnline() } returns true
        val syncPullRepository = mockk<SyncPullRepository>()
        coEvery { syncPullRepository.pull() } returns Result.failure(Exception("sync failed"))
        val tripRepository = mockk<TripRepository>()
        coEvery { tripRepository.refreshTrips() } returns Result.success(Unit)
        val userRepository = mockk<UserRepository>()
        coEvery { userRepository.refreshMe() } returns Result.success(Unit)
        val notificationRepository = mockk<NotificationRepository>()
        coEvery { notificationRepository.refreshNotifications() } returns Result.success(Unit)
        coEvery { notificationRepository.notifications } returns flowOf(emptyList<NotificationDto>())
        val systemNotificationManager = mockk<SystemNotificationManager>(relaxed = true)
        val worker = createWorker(
            sessionStore = sessionStore,
            networkStateProvider = networkStateProvider,
            tripRepository = tripRepository,
            userRepository = userRepository,
            syncPullRepository = syncPullRepository,
            notificationRepository = notificationRepository,
            systemNotificationManager = systemNotificationManager,
        )

        // WHEN
        val result = worker.doWork()

        // THEN
        assertEquals(androidx.work.ListenableWorker.Result.retry(), result)
    }

    private fun createWorker(
        sessionStore: SessionStore = mockk(relaxed = true),
        networkStateProvider: NetworkStateProvider = mockk { every { isOnline() } returns true },
        tripRepository: TripRepository = mockk { coEvery { refreshTrips() } returns kotlin.Result.success(Unit) },
        userRepository: UserRepository = mockk { coEvery { refreshMe() } returns kotlin.Result.success(Unit) },
        syncPullRepository: SyncPullRepository = mockk { coEvery { pull() } returns kotlin.Result.success(Unit) },
        notificationRepository: NotificationRepository = mockk {
            coEvery { refreshNotifications() } returns kotlin.Result.success(Unit)
            coEvery { notifications } returns flowOf(emptyList())
        },
        systemNotificationManager: SystemNotificationManager = mockk(relaxed = true),
    ): RefreshWorker {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val params = mockk<WorkerParameters>(relaxed = true)
        return RefreshWorker(
            context,
            params,
            sessionStore,
            networkStateProvider,
            tripRepository,
            userRepository,
            syncPullRepository,
            notificationRepository,
            systemNotificationManager,
        )
    }
}
