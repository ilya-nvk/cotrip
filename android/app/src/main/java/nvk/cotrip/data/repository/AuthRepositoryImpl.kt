package nvk.cotrip.data.repository

import nvk.cotrip.data.auth.SessionCleaner
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.cache.UserCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.AuthDevRequest
import nvk.cotrip.data.network.dto.AuthGoogleRequest
import nvk.cotrip.data.network.dto.AuthResponse
import nvk.cotrip.data.network.requireSuccess
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val sessionStore: SessionStore,
    private val sessionCleaner: SessionCleaner,
    private val userCacheStore: UserCacheStore,
) : AuthRepository {

    override fun hasSession(): Boolean {
        return sessionStore.hasSession()
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResponse {
        val response = api.googleAuth(AuthGoogleRequest(idToken))
        sessionStore.setTokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
        )
        userCacheStore.setUser(response.user)
        return response
    }

    override suspend fun signInWithDev(request: AuthDevRequest): AuthResponse {
        val response = api.devAuth(request)
        sessionStore.setTokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
        )
        userCacheStore.setUser(response.user)
        return response
    }

    override suspend fun logout() {
        runCatching {
            api.logout().requireSuccess()
        }
    }

    override fun clearSession() {
        sessionCleaner.clearSessionBlocking()
    }
}
