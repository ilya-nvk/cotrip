package nvk.cotrip.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class DataStoreSessionStore(
    private val dataStore: DataStore<Preferences>,
) : SessionStore {

    @Volatile
    private var cachedToken: String? = null

    init {
        cachedToken = runBlocking {
            dataStore.data.first()[TOKEN_KEY]
        }
    }

    override fun getAccessToken(): String? = cachedToken

    override fun setAccessToken(token: String) {
        cachedToken = token
        runBlocking {
            dataStore.edit { prefs ->
                prefs[TOKEN_KEY] = token
            }
        }
    }

    override fun clear() {
        cachedToken = null
        runBlocking {
            dataStore.edit { prefs ->
                prefs.remove(TOKEN_KEY)
            }
        }
    }

    private companion object {
        private val TOKEN_KEY = stringPreferencesKey("access_token")
    }
}
