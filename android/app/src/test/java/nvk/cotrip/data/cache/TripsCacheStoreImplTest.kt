package nvk.cotrip.data.cache

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.dto.TripDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class TripsCacheStoreImplTest {

    private lateinit var store: TripsCacheStoreImpl
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreFile: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        dataStoreFile = File.createTempFile("trips_cache_test", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { dataStoreFile },
            scope = scope,
        )
        store = TripsCacheStoreImpl(dataStore, json)
    }

    @After
    fun tearDown() {
        dataStoreFile.delete()
    }

    @Test
    fun given_emptyStore_when_getTrips_then_returnsEmptyList() = runTest {
        // GIVEN — fresh store

        // WHEN
        val result = store.getTrips()

        // THEN
        assertTrue(result.isEmpty())
    }

    @Test
    fun given_setTrips_when_getTrips_then_returnsSameList() = runTest {
        // GIVEN
        val trips = listOf(
            tripDto("trip-1", "Trip One"),
            tripDto("trip-2", "Trip Two"),
        )

        // WHEN
        store.setTrips(trips)
        val result = store.getTrips()

        // THEN
        assertEquals(2, result.size)
        assertEquals("trip-1", result[0].id)
        assertEquals("Trip One", result[0].title)
        assertEquals("trip-2", result[1].id)
    }

    @Test
    fun given_tripsSet_when_upsertExisting_then_updatesInPlace() = runTest {
        // GIVEN
        store.setTrips(listOf(tripDto("trip-1", "Original")))
        val updated = tripDto("trip-1", "Updated Title")

        // WHEN
        store.upsertTrip(updated)
        val result = store.getTrips()

        // THEN
        assertEquals(1, result.size)
        assertEquals("Updated Title", result[0].title)
    }

    @Test
    fun given_tripsSet_when_upsertNew_then_prepends() = runTest {
        // GIVEN
        store.setTrips(listOf(tripDto("trip-1", "First")))

        // WHEN
        store.upsertTrip(tripDto("trip-2", "New"))
        val result = store.getTrips()

        // THEN
        assertEquals(2, result.size)
        assertEquals("trip-2", result[0].id)
        assertEquals("trip-1", result[1].id)
    }

    @Test
    fun given_tripsSet_when_removeTrip_then_removesById() = runTest {
        // GIVEN
        store.setTrips(listOf(
            tripDto("trip-1", "One"),
            tripDto("trip-2", "Two"),
        ))

        // WHEN
        store.removeTrip("trip-1")
        val result = store.getTrips()

        // THEN
        assertEquals(1, result.size)
        assertEquals("trip-2", result[0].id)
    }

    @Test
    fun given_tripsSet_when_clear_then_getTripsReturnsEmpty() = runTest {
        // GIVEN
        store.setTrips(listOf(tripDto("trip-1", "One")))

        // WHEN
        store.clear()
        val result = store.getTrips()

        // THEN
        assertTrue(result.isEmpty())
    }

    @Test
    fun given_tripsSet_when_tripsFlow_then_emitsCurrentList() = runTest {
        // GIVEN
        store.setTrips(listOf(tripDto("trip-1", "One")))

        // WHEN
        val list = store.trips.first()

        // THEN
        assertEquals(1, list.size)
        assertEquals("trip-1", list[0].id)
    }

    @Test
    fun given_invalidJsonInStore_when_getTrips_then_returnsEmptyList() = runTest {
        // GIVEN — write invalid JSON under the same key the impl uses
        val key = stringPreferencesKey("trips_cache")
        dataStore.edit { it[key] = "{invalid" }

        // WHEN
        val result = store.getTrips()

        // THEN — decode falls back to emptyList()
        assertTrue(result.isEmpty())
    }

    private fun tripDto(id: String, title: String): TripDto = TripDto(
        id = id,
        ownerId = "owner-1",
        title = title,
        description = null,
        startDate = "2026-06-01",
        endDate = "2026-06-02",
        locationLine = null,
        coverUrl = null,
        currencyCode = "EUR",
        status = "active",
        updatedAt = "2026-01-01T00:00:00Z",
    )
}
