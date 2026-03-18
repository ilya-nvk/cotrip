package nvk.cotrip.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.dto.ItineraryDayDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class ItineraryCacheStoreImplTest {

    private lateinit var store: ItineraryCacheStoreImpl
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreFile: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        dataStoreFile = File.createTempFile("itinerary_cache_test", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { dataStoreFile },
            scope = scope,
        )
        store = ItineraryCacheStoreImpl(dataStore, json)
    }

    @After
    fun tearDown() {
        dataStoreFile.delete()
    }

    @Test
    fun given_emptyStore_when_getItinerary_then_returnsEmptyList() = runTest {
        val result = store.getItinerary("trip-1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun given_setItinerary_when_getItinerary_then_returnsSameList() = runTest {
        val days = listOf(dayDto("day-1", "trip-1", "2026-06-01", 1), dayDto("day-2", "trip-1", "2026-06-02", 2))
        store.setItinerary("trip-1", days)
        val result = store.getItinerary("trip-1")
        assertEquals(2, result.size)
        assertEquals("day-1", result[0].id)
        assertEquals(1, result[0].dayNumber)
    }

    @Test
    fun given_itinerarySet_when_updateItinerary_then_transformsList() = runTest {
        store.setItinerary("trip-1", listOf(dayDto("day-1", "trip-1", "2026-06-01", 1)))
        store.updateItinerary("trip-1") { list -> list + dayDto("day-2", "trip-1", "2026-06-02", 2) }
        val result = store.getItinerary("trip-1")
        assertEquals(2, result.size)
    }

    @Test
    fun given_itinerarySet_when_clearTrip_then_getItineraryReturnsEmpty() = runTest {
        store.setItinerary("trip-1", listOf(dayDto("day-1", "trip-1", "2026-06-01", 1)))
        store.clearTrip("trip-1")
        assertTrue(store.getItinerary("trip-1").isEmpty())
    }

    @Test
    fun given_itinerarySet_when_clearAll_then_getAllEmpty() = runTest {
        store.setItinerary("trip-1", listOf(dayDto("day-1", "trip-1", "2026-06-01", 1)))
        store.setItinerary("trip-2", listOf(dayDto("day-2", "trip-2", "2026-06-01", 1)))
        store.clearAll()
        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun given_itinerarySet_when_getAll_then_returnsByTrip() = runTest {
        store.setItinerary("trip-1", listOf(dayDto("day-1", "trip-1", "2026-06-01", 1)))
        store.setItinerary("trip-2", listOf(dayDto("day-2", "trip-2", "2026-06-01", 1)))
        val all = store.getAll()
        assertEquals(2, all.size)
        assertEquals(1, all["trip-1"]!!.size)
        assertEquals(1, all["trip-2"]!!.size)
    }

    @Test
    fun given_invalidJsonInStore_when_getItinerary_then_returnsEmptyList() = runTest {
        val key = stringPreferencesKey("itinerary_cache")
        dataStore.edit { it[key] = "{invalid" }
        val result = store.getItinerary("trip-1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun given_itinerarySet_when_observeItinerary_then_emitsCurrentList() = runTest {
        store.setItinerary("trip-1", listOf(dayDto("day-1", "trip-1", "2026-06-01", 1)))
        val list = store.observeItinerary("trip-1").first()
        assertEquals(1, list.size)
        assertEquals("day-1", list[0].id)
    }

    private fun dayDto(id: String, tripId: String, date: String, dayNumber: Int): ItineraryDayDto =
        ItineraryDayDto(
            id = id,
            tripId = tripId,
            date = date,
            dayNumber = dayNumber,
            city = null,
            cityProviderId = null,
            cityLat = null,
            cityLon = null,
            isOutOfRange = false,
            activities = emptyList(),
        )
}
