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
import nvk.cotrip.data.network.dto.IdeaDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class IdeasCacheStoreImplTest {

    private lateinit var store: IdeasCacheStoreImpl
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var dataStoreFile: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        dataStoreFile = File.createTempFile("ideas_cache_test", ".preferences_pb")
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { dataStoreFile },
            scope = scope,
        )
        store = IdeasCacheStoreImpl(dataStore, json)
    }

    @After
    fun tearDown() {
        dataStoreFile.delete()
    }

    @Test
    fun given_emptyStore_when_getIdeas_then_returnsEmptyList() = runTest {
        val result = store.getIdeas("trip-1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun given_setIdeas_when_getIdeas_then_returnsSameList() = runTest {
        val ideas = listOf(ideaDto("idea-1", "trip-1", "Idea One"), ideaDto("idea-2", "trip-1", "Idea Two"))
        store.setIdeas("trip-1", ideas)
        val result = store.getIdeas("trip-1")
        assertEquals(2, result.size)
        assertEquals("idea-1", result[0].id)
        assertEquals("Idea One", result[0].title)
    }

    @Test
    fun given_ideasSet_when_upsertExisting_then_updatesInPlace() = runTest {
        store.setIdeas("trip-1", listOf(ideaDto("idea-1", "trip-1", "Original")))
        store.upsertIdea("trip-1", ideaDto("idea-1", "trip-1", "Updated"))
        val result = store.getIdeas("trip-1")
        assertEquals(1, result.size)
        assertEquals("Updated", result[0].title)
    }

    @Test
    fun given_ideasSet_when_upsertNew_then_prepends() = runTest {
        store.setIdeas("trip-1", listOf(ideaDto("idea-1", "trip-1", "First")))
        store.upsertIdea("trip-1", ideaDto("idea-2", "trip-1", "New"))
        val result = store.getIdeas("trip-1")
        assertEquals(2, result.size)
        assertEquals("idea-2", result[0].id)
    }

    @Test
    fun given_ideasSet_when_removeIdea_then_removesById() = runTest {
        store.setIdeas("trip-1", listOf(
            ideaDto("idea-1", "trip-1", "One"),
            ideaDto("idea-2", "trip-1", "Two"),
        ))
        store.removeIdea("trip-1", "idea-1")
        val result = store.getIdeas("trip-1")
        assertEquals(1, result.size)
        assertEquals("idea-2", result[0].id)
    }

    @Test
    fun given_ideasSet_when_clearTrip_then_getIdeasReturnsEmpty() = runTest {
        store.setIdeas("trip-1", listOf(ideaDto("idea-1", "trip-1", "One")))
        store.clearTrip("trip-1")
        assertTrue(store.getIdeas("trip-1").isEmpty())
    }

    @Test
    fun given_ideasSet_when_clearAll_then_allTripsEmpty() = runTest {
        store.setIdeas("trip-1", listOf(ideaDto("idea-1", "trip-1", "One")))
        store.setIdeas("trip-2", listOf(ideaDto("idea-2", "trip-2", "Two")))
        store.clearAll()
        assertTrue(store.getIdeas("trip-1").isEmpty())
        assertTrue(store.getIdeas("trip-2").isEmpty())
    }

    @Test
    fun given_ideasSet_when_findIdeaById_then_returnsIdea() = runTest {
        store.setIdeas("trip-1", listOf(ideaDto("idea-1", "trip-1", "One")))
        val found = store.findIdeaById("idea-1")
        assertTrue(found != null)
        assertEquals("One", found!!.title)
    }

    @Test
    fun given_emptyStore_when_findIdeaById_then_returnsNull() = runTest {
        assertNull(store.findIdeaById("missing"))
    }

    @Test
    fun given_invalidJsonInStore_when_getIdeas_then_returnsEmptyList() = runTest {
        val key = stringPreferencesKey("ideas_cache")
        dataStore.edit { it[key] = "{invalid" }
        val result = store.getIdeas("trip-1")
        assertTrue(result.isEmpty())
    }

    @Test
    fun given_ideasSet_when_observeIdeas_then_emitsCurrentList() = runTest {
        store.setIdeas("trip-1", listOf(ideaDto("idea-1", "trip-1", "One")))
        val list = store.observeIdeas("trip-1").first()
        assertEquals(1, list.size)
        assertEquals("idea-1", list[0].id)
    }

    private fun ideaDto(id: String, tripId: String, title: String): IdeaDto = IdeaDto(
        id = id,
        tripId = tripId,
        authorId = "author-1",
        title = title,
        city = null,
        link = null,
        costAmount = null,
        costType = null,
        notes = null,
        status = "open",
        updatedAt = "2026-01-01T00:00:00Z",
        commentsCount = 0,
    )
}
