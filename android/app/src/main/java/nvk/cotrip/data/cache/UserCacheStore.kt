package nvk.cotrip.data.cache

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.UserDto

interface UserCacheStore {
    val user: Flow<UserDto?>

    suspend fun getUser(): UserDto?
    suspend fun setUser(user: UserDto?)
    suspend fun clear()
}
