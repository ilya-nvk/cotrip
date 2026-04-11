package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import nvk.cotrip.data.auth.SessionCleaner
import nvk.cotrip.data.cache.UserCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.network.dto.UserDto
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import nvk.cotrip.data.sync.SyncUserProfileUpsertPayload
import nvk.cotrip.notifications.PushTokenSyncManager
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val sessionCleaner: SessionCleaner,
    private val userCacheStore: UserCacheStore,
    private val pushTokenSyncManager: PushTokenSyncManager,
    private val syncQueueRepository: SyncQueueRepository,
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
        val user = try {
            api.updateMe(request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(
                entity = SyncEntities.USER_PROFILE,
                id = "me",
                payload = SyncUserProfileUpsertPayload(
                    name = request.name,
                    photoUrl = request.photoUrl,
                ),
            )
            val existing = runCatching { userCacheStore.getUser() }.getOrNull()
            val local = if (existing != null) {
                existing.copy(
                    name = request.name,
                    photoUrl = request.photoUrl,
                    initials = initialsFromName(request.name),
                )
            } else {
                UserDto(
                    id = "me",
                    name = request.name,
                    photoUrl = request.photoUrl,
                    initials = initialsFromName(request.name),
                )
            }
            safeLocalMutation("updateMe.offlineSetUser") {
                userCacheStore.setUser(local)
            }
            return local
        }
        safeLocalMutation("updateMe.setUser") {
            userCacheStore.setUser(user)
        }
        return user
    }

    override suspend fun deleteMe() {
        runCatching { pushTokenSyncManager.unregisterRememberedToken() }
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
            runBlocking { pushTokenSyncManager.unregisterRememberedToken() }
        }
        runCatching {
            sessionCleaner.clearSessionBlocking()
        }.onFailure {
            AppLogger.w(TAG, "clearSession cache clear failed", it)
        }
    }
}

private fun initialsFromName(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}
