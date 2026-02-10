package nvk.cotrip.data.repository

import javax.inject.Inject
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.UpsertPushTokenRequest

class PushTokenRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
) : PushTokenRepository {
    override suspend fun upsert(token: String, platform: String) {
        api.upsertPushToken(
            UpsertPushTokenRequest(
                token = token,
                platform = platform
            )
        )
    }

    override suspend fun delete(token: String) {
        api.deletePushToken(token)
    }
}
