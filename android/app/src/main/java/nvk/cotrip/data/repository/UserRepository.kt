package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.network.dto.UserDto

interface UserRepository {
    val me: Flow<UserDto?>

    suspend fun getMe(): UserDto
    suspend fun refreshMe(): Result<Unit>
    suspend fun updateMe(request: UpdateUserRequest): UserDto
    suspend fun deleteMe()
    fun clearSession()
}
