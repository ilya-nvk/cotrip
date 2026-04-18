package nvk.cotrip.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import nvk.cotrip.data.cache.InviteCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.InviteInfoDto
import nvk.cotrip.data.network.dto.InviteLinkDto
import nvk.cotrip.util.AppLogger
import javax.inject.Inject

class InviteRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val inviteCacheStore: InviteCacheStore,
    private val networkStateProvider: NetworkStateProvider,
) : InviteRepository {

    private companion object {
        private const val TAG = "InviteRepository"
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override suspend fun createInvite(tripId: String): InviteLinkDto {
        return api.createInvite(tripId)
    }

    override suspend fun getInviteForJoin(token: String): InviteInfoDto? {
        return if (networkStateProvider.isOnline()) {
            runCatching { api.getInvite(token) }
                .onSuccess { invite ->
                    safeLocalMutation("getInviteForJoin.setInvite(token=$token)") {
                        inviteCacheStore.setInvite(token, invite)
                    }
                }
                .getOrNull() ?: inviteCacheStore.getInvite(token)
        } else {
            inviteCacheStore.getInvite(token)
        }
    }

    override fun getInvite(token: String): Flow<InviteInfoDto> {
        if (networkStateProvider.isOnline()) {
            ioScope.launch {
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
        }
        return inviteCacheStore.observeInvite(token).mapNotNull { it }
    }

    override suspend fun acceptInvite(token: String): String {
        return api.acceptInvite(token)["tripId"].orEmpty()
    }

    override suspend fun joinTripById(tripId: String): String {
        return api.joinTripById(tripId)["tripId"].orEmpty()
    }
}
