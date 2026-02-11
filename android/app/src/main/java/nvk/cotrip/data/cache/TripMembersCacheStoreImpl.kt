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
import nvk.cotrip.data.network.dto.MemberDto
import javax.inject.Inject

class TripMembersCacheStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : TripMembersCacheStore {

    override fun observeMembers(tripId: String): Flow<List<MemberDto>> {
        return dataStore.data.map { prefs ->
            decodeCache(prefs[MEMBERS_KEY]).byTrip[tripId].orEmpty()
        }
    }

    override suspend fun getMembers(tripId: String): List<MemberDto> {
        val prefs = dataStore.data.first()
        return decodeCache(prefs[MEMBERS_KEY]).byTrip[tripId].orEmpty()
    }

    override suspend fun setMembers(tripId: String, members: List<MemberDto>) {
        updateCache { cache ->
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { put(tripId, members) })
        }
    }

    override suspend fun removeMember(tripId: String, memberId: String) {
        updateCache { cache ->
            val remaining = cache.byTrip[tripId].orEmpty().filterNot { it.userId == memberId }
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { put(tripId, remaining) })
        }
    }

    override suspend fun clearTrip(tripId: String) {
        updateCache { cache ->
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { remove(tripId) })
        }
    }

    override suspend fun clearAll() {
        updateCache { cache -> cache.copy(byTrip = emptyMap()) }
    }

    private suspend fun updateCache(transform: (TripMembersCache) -> TripMembersCache) {
        dataStore.edit { prefs ->
            val current = decodeCache(prefs[MEMBERS_KEY])
            val updated = transform(current)
            prefs[MEMBERS_KEY] = json.encodeToString(TripMembersCache.serializer(), updated)
        }
    }

    private fun decodeCache(raw: String?): TripMembersCache {
        if (raw.isNullOrBlank()) return TripMembersCache()
        return runCatching { json.decodeFromString(TripMembersCache.serializer(), raw) }
            .getOrElse { TripMembersCache() }
    }

    @Serializable
    private data class TripMembersCache(
        val byTrip: Map<String, List<MemberDto>> = emptyMap(),
    )

    private companion object {
        private val MEMBERS_KEY = stringPreferencesKey("trip_members_cache")
    }
}

