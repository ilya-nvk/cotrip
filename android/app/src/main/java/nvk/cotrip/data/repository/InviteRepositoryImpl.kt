package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nvk.cotrip.data.cache.InviteCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.InviteInfoDto
import nvk.cotrip.data.network.dto.InviteLinkDto
import nvk.cotrip.util.AppLogger
import java.io.IOException
import javax.inject.Inject

class InviteRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val inviteCacheStore: InviteCacheStore,
    private val networkStateProvider: NetworkStateProvider,
) : InviteRepository {

    private companion object {
        private const val TAG = "InviteRepository"
    }

    override suspend fun createInvite(tripId: String): InviteLinkDto {
        return api.createInvite(tripId)
    }

    override suspend fun getInvite(token: String): Flow<InviteInfoDto> {
        if (networkStateProvider.isOnline()) {
            runCatching { api.getInvite(token) }
                .onSuccess { invite ->
                    safeLocalMutation("getInvite.setInvite(token=$token)") {
                        inviteCacheStore.setInvite(token, invite)
                    }
                }
                .onFailure { error ->
                    AppLogger.w(TAG, "getInvite network fetch failed token=$token", error)
                }
        }
        return inviteCacheStore.observeInvite(token).map { cached ->
            cached ?: throw IOException("Invite token $token is not available in cache")
        }
    }

    override suspend fun acceptInvite(token: String): String {
        return api.acceptInvite(token)["tripId"].orEmpty()
    }

    override suspend fun joinTripById(tripId: String): String {
        return api.joinTripById(tripId)["tripId"].orEmpty()
    }
}
