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
import nvk.cotrip.data.network.dto.ItineraryDayDto
import javax.inject.Inject

class ItineraryCacheStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : ItineraryCacheStore {

    override fun observeItinerary(tripId: String): Flow<List<ItineraryDayDto>> {
        return dataStore.data.map { prefs ->
            decodeCache(prefs[ITINERARY_KEY]).byTrip[tripId].orEmpty()
        }
    }

    override suspend fun getItinerary(tripId: String): List<ItineraryDayDto> {
        val prefs = dataStore.data.first()
        return decodeCache(prefs[ITINERARY_KEY]).byTrip[tripId].orEmpty()
    }

    override suspend fun getAll(): Map<String, List<ItineraryDayDto>> {
        val prefs = dataStore.data.first()
        return decodeCache(prefs[ITINERARY_KEY]).byTrip
    }

    override suspend fun setItinerary(tripId: String, days: List<ItineraryDayDto>) {
        updateCache { cache ->
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { put(tripId, days) })
        }
    }

    override suspend fun updateItinerary(
        tripId: String,
        transform: (List<ItineraryDayDto>) -> List<ItineraryDayDto>
    ) {
        updateCache { cache ->
            val current = cache.byTrip[tripId].orEmpty()
            val updated = transform(current)
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { put(tripId, updated) })
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

    private suspend fun updateCache(transform: (ItineraryCache) -> ItineraryCache) {
        dataStore.edit { prefs ->
            val current = decodeCache(prefs[ITINERARY_KEY])
            val updated = transform(current)
            prefs[ITINERARY_KEY] = json.encodeToString(ItineraryCache.serializer(), updated)
        }
    }

    private fun decodeCache(raw: String?): ItineraryCache {
        if (raw.isNullOrBlank()) return ItineraryCache()
        return runCatching { json.decodeFromString(ItineraryCache.serializer(), raw) }
            .getOrElse { ItineraryCache() }
    }

    @Serializable
    private data class ItineraryCache(
        val byTrip: Map<String, List<ItineraryDayDto>> = emptyMap(),
    )

    private companion object {
        private val ITINERARY_KEY = stringPreferencesKey("itinerary_cache")
    }
}
