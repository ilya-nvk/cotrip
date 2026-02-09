package nvk.cotrip.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.dto.UserDto
import javax.inject.Inject

class UserCacheStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : UserCacheStore {

    override val user: Flow<UserDto?> = dataStore.data.map { prefs ->
        decodeUser(prefs[USER_KEY])
    }

    override suspend fun getUser(): UserDto? {
        val prefs = dataStore.data.first()
        return decodeUser(prefs[USER_KEY])
    }

    override suspend fun setUser(user: UserDto?) {
        dataStore.edit { prefs ->
            if (user == null) {
                prefs.remove(USER_KEY)
            } else {
                prefs[USER_KEY] = json.encodeToString(UserDto.serializer(), user)
            }
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(USER_KEY) }
    }

    private fun decodeUser(raw: String?): UserDto? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(UserDto.serializer(), raw) }.getOrNull()
    }

    private companion object {
        private val USER_KEY = stringPreferencesKey("user_cache")
    }
}
