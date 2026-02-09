package nvk.cotrip.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.dto.TripDto
import javax.inject.Inject

class TripsCacheStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : TripsCacheStore {

    override val trips: Flow<List<TripDto>> = dataStore.data.map { prefs ->
        decodeTrips(prefs[TRIPS_KEY])
    }

    override suspend fun getTrips(): List<TripDto> {
        val prefs = dataStore.data.first()
        return decodeTrips(prefs[TRIPS_KEY])
    }

    override suspend fun setTrips(trips: List<TripDto>) {
        dataStore.edit { prefs ->
            prefs[TRIPS_KEY] = encodeTrips(trips)
        }
    }

    override suspend fun upsertTrip(trip: TripDto) {
        val current = getTrips().toMutableList()
        val index = current.indexOfFirst { it.id == trip.id }
        if (index >= 0) {
            current[index] = trip
        } else {
            current.add(0, trip)
        }
        setTrips(current)
    }

    override suspend fun removeTrip(tripId: String) {
        val current = getTrips().filterNot { it.id == tripId }
        setTrips(current)
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(TRIPS_KEY) }
    }

    private fun encodeTrips(trips: List<TripDto>): String {
        return json.encodeToString(ListSerializer(TripDto.serializer()), trips)
    }

    private fun decodeTrips(raw: String?): List<TripDto> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(TripDto.serializer()), raw)
        }.getOrElse { emptyList() }
    }

    private companion object {
        private val TRIPS_KEY = stringPreferencesKey("trips_cache")
    }
}
