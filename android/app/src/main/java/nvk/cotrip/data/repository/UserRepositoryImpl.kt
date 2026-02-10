package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.cache.ExpensesCacheStore
import nvk.cotrip.data.cache.IdeasCacheStore
import nvk.cotrip.data.cache.ItineraryCacheStore
import nvk.cotrip.data.cache.TripsCacheStore
import nvk.cotrip.data.cache.UserCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.network.dto.UserDto
import nvk.cotrip.data.sync.SyncStateStore
import kotlinx.coroutines.runBlocking
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
) : UserRepository {

    override val me: Flow<UserDto?> = userCacheStore.user

    override suspend fun getMe(): UserDto {
        val user = api.getMe()
        userCacheStore.setUser(user)
        return user
    }

    override suspend fun refreshMe(): Result<Unit> {
        return runCatching {
            val user = api.getMe()
            userCacheStore.setUser(user)
        }
    }

    override suspend fun updateMe(request: UpdateUserRequest): UserDto {
        val user = api.updateMe(request)
        userCacheStore.setUser(user)
        return user
    }

    override suspend fun deleteMe() {
        api.deleteMe()
        clearAllCaches()
    }

    override fun clearSession() {
        sessionStore.clear()
        runCatching {
            runBlocking { clearAllCaches() }
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
