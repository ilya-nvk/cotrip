package nvk.cotrip.data.repository

import javax.inject.Inject
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.InviteInfoDto
import nvk.cotrip.data.network.dto.InviteLinkDto

class InviteRepository @Inject constructor(
    private val api: CoTripApi,
) {
    suspend fun createInvite(tripId: String): InviteLinkDto {
        return api.createInvite(tripId)
    }

    suspend fun getInvite(token: String): InviteInfoDto {
        return api.getInvite(token)
    }

    suspend fun acceptInvite(token: String): String {
        return api.acceptInvite(token)["tripId"].orEmpty()
    }
}
