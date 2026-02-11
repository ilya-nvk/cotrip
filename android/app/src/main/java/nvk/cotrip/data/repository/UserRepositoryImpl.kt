package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import nvk.cotrip.data.auth.SessionStore
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
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.network.dto.UserDto
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.data.sync.SyncStateStore
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
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
) : UserRepository {

    private companion object {
        private const val TAG = "UserRepository"
    }

    override val me: Flow<UserDto?> = userCacheStore.user

    override suspend fun refreshMe(): Result<Unit> {
        return try {
            val user = api.getMe()
            safeLocalMutation("refreshMe.setUser") {
                userCacheStore.setUser(user)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateMe(request: UpdateUserRequest): UserDto {
        val user = api.updateMe(request)
        safeLocalMutation("updateMe.setUser") {
            userCacheStore.setUser(user)
        }
        return user
    }

    override suspend fun deleteMe() {
        try {
            api.deleteMe().requireSuccess()
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            AppLogger.i(TAG, "deleteMe got 404, treating as already deleted")
        }
        safeLocalMutation("deleteMe.clearAllCaches") {
            clearAllCaches()
        }
    }

    override fun clearSession() {
        sessionStore.clear()
        runCatching {
            runBlocking { clearAllCaches() }
        }.onFailure {
            AppLogger.w(TAG, "clearSession cache clear failed", it)
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
