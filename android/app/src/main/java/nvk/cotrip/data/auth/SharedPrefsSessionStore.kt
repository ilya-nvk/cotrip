package nvk.cotrip.data.auth

import android.content.SharedPreferences

class SharedPrefsSessionStore(
    private val prefs: SharedPreferences,
) : SessionStore {

    override fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    override fun setAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_ACCESS_TOKEN).apply()
    }

    private companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
    }
}
