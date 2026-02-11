package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.InviteInfoDto
import nvk.cotrip.data.network.dto.InviteLinkDto

interface InviteRepository {
    suspend fun createInvite(tripId: String): InviteLinkDto
    suspend fun getInvite(token: String): Flow<InviteInfoDto>
    suspend fun acceptInvite(token: String): String
    suspend fun joinTripById(tripId: String): String
}
