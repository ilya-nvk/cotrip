package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.auth.SessionCleaner
import nvk.cotrip.data.cache.UserCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.network.dto.UserDto
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val sessionCleaner: SessionCleaner,
    private val userCacheStore: UserCacheStore,
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
            sessionCleaner.clearSession()
        }
    }

    override fun clearSession() {
        runCatching {
            sessionCleaner.clearSessionBlocking()
        }.onFailure {
            AppLogger.w(TAG, "clearSession cache clear failed", it)
        }
    }
}
