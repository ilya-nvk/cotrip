package nvk.cotrip.data.auth

import kotlinx.coroutines.flow.StateFlow

interface SessionStore {
    fun getAccessToken(): String?
    fun getRefreshToken(): String?
    fun setTokens(accessToken: String, refreshToken: String)
    fun setAccessToken(token: String)
    fun hasSession(): Boolean
    val isAuthenticated: StateFlow<Boolean>
    fun clear()
}
