package nvk.cotrip.data.cache

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.InviteInfoDto

interface InviteCacheStore {
    fun observeInvite(token: String): Flow<InviteInfoDto?>
    suspend fun getInvite(token: String): InviteInfoDto?
    suspend fun setInvite(token: String, invite: InviteInfoDto)
    suspend fun clear()
}
