package nvk.cotrip.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingTripCreationStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    suspend fun getPendingTripId(): String? {
        return dataStore.data.first()[PENDING_TRIP_ID]
    }

    suspend fun setPendingTripId(tripId: String) {
        dataStore.edit { prefs ->
            prefs[PENDING_TRIP_ID] = tripId
        }
    }

    suspend fun clearPendingTripId(tripId: String? = null) {
        dataStore.edit { prefs ->
            val current = prefs[PENDING_TRIP_ID]
            if (tripId == null || current == tripId) {
                prefs.remove(PENDING_TRIP_ID)
            }
        }
    }

    private companion object {
        private val PENDING_TRIP_ID = stringPreferencesKey("pending_trip_creation_id")
    }
}
