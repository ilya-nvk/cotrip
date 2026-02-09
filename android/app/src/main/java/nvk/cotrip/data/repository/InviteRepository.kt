package nvk.cotrip.data.repository

import nvk.cotrip.data.network.dto.InviteInfoDto
import nvk.cotrip.data.network.dto.InviteLinkDto

interface InviteRepository {
    suspend fun createInvite(tripId: String): InviteLinkDto
    suspend fun getInvite(token: String): InviteInfoDto
    suspend fun acceptInvite(token: String): String
}
