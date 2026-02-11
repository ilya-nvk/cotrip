package nvk.cotrip.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.dto.InviteInfoDto
import javax.inject.Inject

class InviteCacheStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : InviteCacheStore {

    override fun observeInvite(token: String): Flow<InviteInfoDto?> {
        return dataStore.data.map { prefs ->
            decodeCache(prefs[INVITES_KEY]).byToken[token]
        }
    }

    override suspend fun getInvite(token: String): InviteInfoDto? {
        val prefs = dataStore.data.first()
        return decodeCache(prefs[INVITES_KEY]).byToken[token]
    }

    override suspend fun setInvite(token: String, invite: InviteInfoDto) {
        updateCache { cache ->
            cache.copy(byToken = cache.byToken.toMutableMap().apply { put(token, invite) })
        }
    }

    override suspend fun clear() {
        updateCache { cache -> cache.copy(byToken = emptyMap()) }
    }

    private suspend fun updateCache(transform: (InvitesCache) -> InvitesCache) {
        dataStore.edit { prefs ->
            val current = decodeCache(prefs[INVITES_KEY])
            val updated = transform(current)
            prefs[INVITES_KEY] = json.encodeToString(InvitesCache.serializer(), updated)
        }
    }

    private fun decodeCache(raw: String?): InvitesCache {
        if (raw.isNullOrBlank()) return InvitesCache()
        return runCatching { json.decodeFromString(InvitesCache.serializer(), raw) }
            .getOrElse { InvitesCache() }
    }

    @Serializable
    private data class InvitesCache(
        val byToken: Map<String, InviteInfoDto> = emptyMap(),
    )

    private companion object {
        private val INVITES_KEY = stringPreferencesKey("invites_cache")
    }
}
