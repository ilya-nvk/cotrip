package nvk.cotrip.data.network

import nvk.cotrip.data.auth.SessionCleaner
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.dto.RefreshRequest
import nvk.cotrip.util.AppLogger
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionAuthenticator @Inject constructor(
    private val sessionStore: SessionStore,
    private val authRefreshApi: AuthRefreshApi,
    private val sessionCleaner: SessionCleaner,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (isAuthEndpoint(path)) return null
        if (responseCount(response) >= 2) return null

        val requestToken = response.request.header("Authorization")
            ?.removePrefix("Bearer")
            ?.trim()
            .orEmpty()
        if (requestToken.isBlank()) return null

        synchronized(lock) {
            val latestAccessToken = sessionStore.getAccessToken()
            if (!latestAccessToken.isNullOrBlank() && latestAccessToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $latestAccessToken")
                    .build()
            }

            val refreshToken = sessionStore.getRefreshToken()
            if (refreshToken.isNullOrBlank()) {
                sessionCleaner.clearSessionBlocking()
                return null
            }

            val refreshResponse = runCatching {
                authRefreshApi.refresh(RefreshRequest(refreshToken)).execute()
            }.onFailure {
                AppLogger.w(TAG, "refresh call failed", it)
            }.getOrNull() ?: return null

            if (!refreshResponse.isSuccessful) {
                if (refreshResponse.code() in TERMINAL_AUTH_CODES) {
                    sessionCleaner.clearSessionBlocking()
                }
                return null
            }

            val refreshedTokens = refreshResponse.body() ?: return null
            sessionStore.setTokens(
                accessToken = refreshedTokens.accessToken,
                refreshToken = refreshedTokens.refreshToken,
            )
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${refreshedTokens.accessToken}")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var current: Response? = response
        var count = 1
        while (current?.priorResponse != null) {
            count += 1
            current = current.priorResponse
        }
        return count
    }

    private fun isAuthEndpoint(path: String): Boolean {
        return path == "/v1/auth/refresh" || path == "/v1/auth/google" || path == "/v1/auth/dev"
    }

    private companion object {
        private const val TAG = "SessionAuthenticator"
        private val TERMINAL_AUTH_CODES = setOf(400, 401, 403)
    }
}
