package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.cache.ExpensesCacheStore
import nvk.cotrip.data.cache.IdeasCacheStore
import nvk.cotrip.data.cache.ItineraryCacheStore
import nvk.cotrip.data.cache.TripsCacheStore
import nvk.cotrip.data.cache.UserCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.network.dto.UserDto
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.data.sync.SyncStateStore
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val sessionStore: SessionStore,
    private val userCacheStore: UserCacheStore,
    private val tripsCacheStore: TripsCacheStore,
    private val ideasCacheStore: IdeasCacheStore,
    private val expensesCacheStore: ExpensesCacheStore,
    private val itineraryCacheStore: ItineraryCacheStore,
    private val syncStateStore: SyncStateStore,
    private val networkStateProvider: NetworkStateProvider,
) : UserRepository {

    private companion object {
        private const val TAG = "UserRepository"
    }

    override val me: Flow<UserDto?> = userCacheStore.user

    override suspend fun getMe(): UserDto {
        if (!networkStateProvider.isOnline()) {
            return userCacheStore.getUser()
                ?: throw IOException("User profile is not available offline")
        }
        return try {
            val user = api.getMe()
            safeLocalMutation("getMe.setUser") {
                userCacheStore.setUser(user)
            }
            user
        } catch (e: IOException) {
            userCacheStore.getUser() ?: throw e
        }
    }

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
        expensesCacheStore.clearAll()
        itineraryCacheStore.clearAll()
        syncStateStore.clear()
    }
}
