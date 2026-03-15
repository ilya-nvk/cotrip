package nvk.cotrip.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class DataStoreSessionStore(
    context: Context,
) : SessionStore {
    private val lock = Any()
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @Volatile
    private var cachedAccessToken: String? = sharedPreferences.getString(ACCESS_TOKEN_KEY, null)

    @Volatile
    private var cachedRefreshToken: String? = sharedPreferences.getString(REFRESH_TOKEN_KEY, null)

    private val authenticatedState = MutableStateFlow(hasSessionInternal())

    override fun getAccessToken(): String? = cachedAccessToken

    override fun getRefreshToken(): String? = cachedRefreshToken

    override fun setTokens(accessToken: String, refreshToken: String) {
        synchronized(lock) {
            cachedAccessToken = accessToken
            cachedRefreshToken = refreshToken
            sharedPreferences.edit()
                .putString(ACCESS_TOKEN_KEY, accessToken)
                .putString(REFRESH_TOKEN_KEY, refreshToken)
                .apply()
            authenticatedState.value = hasSessionInternal()
        }
    }

    override fun setAccessToken(token: String) {
        synchronized(lock) {
            cachedAccessToken = token
            sharedPreferences.edit()
                .putString(ACCESS_TOKEN_KEY, token)
                .apply()
            authenticatedState.value = hasSessionInternal()
        }
    }

    override fun hasSession(): Boolean = hasSessionInternal()

    override val isAuthenticated = authenticatedState.asStateFlow()

    override fun clear() {
        synchronized(lock) {
            cachedAccessToken = null
            cachedRefreshToken = null
            sharedPreferences.edit()
                .remove(ACCESS_TOKEN_KEY)
                .remove(REFRESH_TOKEN_KEY)
                .apply()
            authenticatedState.value = hasSessionInternal()
        }
    }

    private fun hasSessionInternal(): Boolean {
        return !cachedRefreshToken.isNullOrBlank()
    }

    private companion object {
        private const val PREFS_NAME = "cotrip_auth_secure"
        private const val ACCESS_TOKEN_KEY = "access_token"
        private const val REFRESH_TOKEN_KEY = "refresh_token"
    }
}
