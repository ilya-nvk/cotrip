package nvk.cotrip.data.repository

import javax.inject.Inject
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.InviteInfoDto
import nvk.cotrip.data.network.dto.InviteLinkDto

class InviteRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
) : InviteRepository {
    override suspend fun createInvite(tripId: String): InviteLinkDto {
        return api.createInvite(tripId)
    }

    override suspend fun getInvite(token: String): InviteInfoDto {
        return api.getInvite(token)
    }

    override suspend fun acceptInvite(token: String): String {
        return api.acceptInvite(token)["tripId"].orEmpty()
    }

    override suspend fun joinTripById(tripId: String): String {
        return api.joinTripById(tripId)["tripId"].orEmpty()
    }
}
