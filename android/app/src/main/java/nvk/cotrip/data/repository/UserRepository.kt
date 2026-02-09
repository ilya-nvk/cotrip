package nvk.cotrip.data.repository

import javax.inject.Inject
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.network.dto.UserDto

class UserRepository @Inject constructor(
    private val api: CoTripApi,
    private val sessionStore: SessionStore,
) {
    suspend fun getMe(): UserDto {
        return api.getMe()
    }

    suspend fun updateMe(request: UpdateUserRequest): UserDto {
        return api.updateMe(request)
    }

    suspend fun deleteMe() {
        api.deleteMe()
    }

    fun clearSession() {
        sessionStore.clear()
    }
}
