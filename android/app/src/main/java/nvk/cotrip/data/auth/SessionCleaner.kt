package nvk.cotrip.data.auth

import kotlinx.coroutines.runBlocking
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionCleaner @Inject constructor(
    private val sessionStore: SessionStore,
    private val userCacheStore: UserCacheStore,
    private val tripsCacheStore: TripsCacheStore,
    private val ideasCacheStore: IdeasCacheStore,
    private val ideaCommentsCacheStore: IdeaCommentsCacheStore,
    private val expensesCacheStore: ExpensesCacheStore,
    private val itineraryCacheStore: ItineraryCacheStore,
    private val tripMembersCacheStore: TripMembersCacheStore,
    private val inviteCacheStore: InviteCacheStore,
    private val weatherCacheStore: WeatherCacheStore,
    private val notificationsCacheStore: NotificationsCacheStore,
    private val syncStateStore: SyncStateStore,
) {
    suspend fun clearSession() {
        sessionStore.clear()
        clearAllCaches()
    }

    fun clearSessionBlocking() {
        runBlocking {
            clearSession()
        }
    }

    private suspend fun clearAllCaches() {
        userCacheStore.clear()
        tripsCacheStore.clear()
        ideasCacheStore.clearAll()
        ideaCommentsCacheStore.clearAll()
        expensesCacheStore.clearAll()
        itineraryCacheStore.clearAll()
        tripMembersCacheStore.clearAll()
        inviteCacheStore.clear()
        weatherCacheStore.clear()
        notificationsCacheStore.clear()
        syncStateStore.clear()
    }
}
