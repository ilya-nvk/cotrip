package nvk.cotrip.data.auth

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import nvk.cotrip.data.cache.ExpensesCacheStore
import nvk.cotrip.data.cache.IdeaCommentsCacheStore
import nvk.cotrip.data.cache.IdeasCacheStore
import nvk.cotrip.data.cache.InviteCacheStore
import nvk.cotrip.data.cache.ItineraryCacheStore
import nvk.cotrip.data.cache.NotificationsCacheStore
import nvk.cotrip.data.cache.TripMembersCacheStore
import nvk.cotrip.data.cache.TripsCacheStore
import nvk.cotrip.data.cache.UserCacheStore
import nvk.cotrip.data.cache.WeatherCacheStore
import nvk.cotrip.data.sync.SyncStateStore
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCleanerTest {

    @Test
    fun given_sessionAndCaches_when_clearSession_then_clearsSessionStoreAndAllCaches() = runTest {
        // GIVEN
        val sessionStore = mockk<SessionStore>(relaxed = true)
        val userCacheStore = mockk<UserCacheStore>(relaxed = true)
        val tripsCacheStore = mockk<TripsCacheStore>(relaxed = true)
        val ideasCacheStore = mockk<IdeasCacheStore>(relaxed = true)
        val ideaCommentsCacheStore = mockk<IdeaCommentsCacheStore>(relaxed = true)
        val expensesCacheStore = mockk<ExpensesCacheStore>(relaxed = true)
        val itineraryCacheStore = mockk<ItineraryCacheStore>(relaxed = true)
        val tripMembersCacheStore = mockk<TripMembersCacheStore>(relaxed = true)
        val inviteCacheStore = mockk<InviteCacheStore>(relaxed = true)
        val weatherCacheStore = mockk<WeatherCacheStore>(relaxed = true)
        val notificationsCacheStore = mockk<NotificationsCacheStore>(relaxed = true)
        val syncStateStore = mockk<SyncStateStore>(relaxed = true)
        coEvery { userCacheStore.clear() } returns Unit
        coEvery { tripsCacheStore.clear() } returns Unit
        coEvery { ideasCacheStore.clearAll() } returns Unit
        coEvery { ideaCommentsCacheStore.clearAll() } returns Unit
        coEvery { expensesCacheStore.clearAll() } returns Unit
        coEvery { itineraryCacheStore.clearAll() } returns Unit
        coEvery { tripMembersCacheStore.clearAll() } returns Unit
        coEvery { inviteCacheStore.clear() } returns Unit
        coEvery { weatherCacheStore.clear() } returns Unit
        coEvery { notificationsCacheStore.clear() } returns Unit
        coEvery { syncStateStore.clear() } returns Unit
        val cleaner = SessionCleaner(
            sessionStore = sessionStore,
            userCacheStore = userCacheStore,
            tripsCacheStore = tripsCacheStore,
            ideasCacheStore = ideasCacheStore,
            ideaCommentsCacheStore = ideaCommentsCacheStore,
            expensesCacheStore = expensesCacheStore,
            itineraryCacheStore = itineraryCacheStore,
            tripMembersCacheStore = tripMembersCacheStore,
            inviteCacheStore = inviteCacheStore,
            weatherCacheStore = weatherCacheStore,
            notificationsCacheStore = notificationsCacheStore,
            syncStateStore = syncStateStore,
        )

        // WHEN
        cleaner.clearSession()

        // THEN
        verify(exactly = 1) { sessionStore.clear() }
        coVerify(exactly = 1) { userCacheStore.clear() }
        coVerify(exactly = 1) { tripsCacheStore.clear() }
        coVerify(exactly = 1) { ideasCacheStore.clearAll() }
        coVerify(exactly = 1) { ideaCommentsCacheStore.clearAll() }
        coVerify(exactly = 1) { expensesCacheStore.clearAll() }
        coVerify(exactly = 1) { itineraryCacheStore.clearAll() }
        coVerify(exactly = 1) { tripMembersCacheStore.clearAll() }
        coVerify(exactly = 1) { inviteCacheStore.clear() }
        coVerify(exactly = 1) { weatherCacheStore.clear() }
        coVerify(exactly = 1) { notificationsCacheStore.clear() }
        coVerify(exactly = 1) { syncStateStore.clear() }
    }

    @Test
    fun given_sessionCleaner_when_clearSessionBlocking_then_invokesClearSession() = runTest {
        // GIVEN
        val sessionStore = mockk<SessionStore>(relaxed = true)
        val userCacheStore = mockk<UserCacheStore>(relaxed = true)
        val tripsCacheStore = mockk<TripsCacheStore>(relaxed = true)
        val ideasCacheStore = mockk<IdeasCacheStore>(relaxed = true)
        val ideaCommentsCacheStore = mockk<IdeaCommentsCacheStore>(relaxed = true)
        val expensesCacheStore = mockk<ExpensesCacheStore>(relaxed = true)
        val itineraryCacheStore = mockk<ItineraryCacheStore>(relaxed = true)
        val tripMembersCacheStore = mockk<TripMembersCacheStore>(relaxed = true)
        val inviteCacheStore = mockk<InviteCacheStore>(relaxed = true)
        val weatherCacheStore = mockk<WeatherCacheStore>(relaxed = true)
        val notificationsCacheStore = mockk<NotificationsCacheStore>(relaxed = true)
        val syncStateStore = mockk<SyncStateStore>(relaxed = true)
        coEvery { userCacheStore.clear() } returns Unit
        coEvery { tripsCacheStore.clear() } returns Unit
        coEvery { ideasCacheStore.clearAll() } returns Unit
        coEvery { ideaCommentsCacheStore.clearAll() } returns Unit
        coEvery { expensesCacheStore.clearAll() } returns Unit
        coEvery { itineraryCacheStore.clearAll() } returns Unit
        coEvery { tripMembersCacheStore.clearAll() } returns Unit
        coEvery { inviteCacheStore.clear() } returns Unit
        coEvery { weatherCacheStore.clear() } returns Unit
        coEvery { notificationsCacheStore.clear() } returns Unit
        coEvery { syncStateStore.clear() } returns Unit
        val cleaner = SessionCleaner(
            sessionStore = sessionStore,
            userCacheStore = userCacheStore,
            tripsCacheStore = tripsCacheStore,
            ideasCacheStore = ideasCacheStore,
            ideaCommentsCacheStore = ideaCommentsCacheStore,
            expensesCacheStore = expensesCacheStore,
            itineraryCacheStore = itineraryCacheStore,
            tripMembersCacheStore = tripMembersCacheStore,
            inviteCacheStore = inviteCacheStore,
            weatherCacheStore = weatherCacheStore,
            notificationsCacheStore = notificationsCacheStore,
            syncStateStore = syncStateStore,
        )

        // WHEN
        cleaner.clearSessionBlocking()

        // THEN
        verify(exactly = 1) { sessionStore.clear() }
        coVerify(exactly = 1) { userCacheStore.clear() }
    }
}
