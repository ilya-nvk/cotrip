package nvk.cotrip.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class SyncStateStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SyncStateStore {
    override suspend fun getLastSync(): String? {
        val prefs = dataStore.data.first()
        return prefs[LAST_SYNC_KEY]
    }

    override suspend fun setLastSync(value: String) {
        dataStore.edit { prefs ->
            prefs[LAST_SYNC_KEY] = value
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(LAST_SYNC_KEY)
        }
    }

    private companion object {
        private val LAST_SYNC_KEY = stringPreferencesKey("sync_last_at")
    }
}
