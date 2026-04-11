package nvk.cotrip.data.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.dto.NotificationSettingDto
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SyncQueueRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: CoTripDatabase
    private lateinit var queue: SyncQueueRepository
    private lateinit var dao: SyncChangeDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CoTripDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        queue = SyncQueueRepository(
            database = database,
            scheduler = NoOpSyncScheduler(context),
            json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
        )
        dao = database.syncChangeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun given_pendingCreateForSameId_when_enqueueDelete_then_queueCompactsToEmpty() = runTest {
        // GIVEN
        queue.enqueueCreate(
            entity = SyncEntities.IDEA,
            id = "idea-1",
            payload = mapOf("title" to "draft")
        )

        // WHEN
        queue.enqueueDelete(entity = SyncEntities.IDEA, id = "idea-1")

        // THEN
        val pending = dao.listPending(limit = 50)
        assertTrue(pending.isEmpty())
    }

    @Test
    fun given_createThenUpsertForSameEntity_when_listPending_then_keepsBothOperations() = runTest {
        // GIVEN
        queue.enqueueCreate(
            entity = SyncEntities.IDEA,
            id = "idea-2",
            payload = mapOf("title" to "first")
        )
        queue.enqueueUpsert(
            entity = SyncEntities.IDEA,
            id = "idea-2",
            payload = UpdateIdeaRequest(title = "edited")
        )

        // WHEN
        val pending = dao.listPending(limit = 50)

        // THEN
        assertEquals(2, pending.size)
        assertEquals(listOf("create", "upsert"), pending.map { it.type })
        assertTrue(pending.all { it.entityId == "idea-2" })
    }

    @Test
    fun given_twoUpsertsForSameEntityId_when_enqueueUpsert_then_onlyLatestIsKept() = runTest {
        // GIVEN
        queue.enqueueUpsert(
            entity = SyncEntities.EXPENSE,
            id = "expense-1",
            payload = mapOf("title" to "first")
        )
        queue.enqueueUpsert(
            entity = SyncEntities.EXPENSE,
            id = "expense-1",
            payload = mapOf("title" to "latest")
        )

        // WHEN
        val pending = dao.listPending(limit = 50)

        // THEN
        assertEquals(1, pending.size)
        assertEquals("upsert", pending.first().type)
        assertEquals("expense-1", pending.first().entityId)
    }

    @Test
    fun given_commandUpsertsForDifferentIds_when_enqueueUpsert_then_keepsSingleLatestItem() = runTest {
        // GIVEN
        queue.enqueueUpsert(
            entity = SyncEntities.NOTIFICATION_SETTINGS,
            id = "settings-1",
            payload = SyncNotificationSettingsUpsertPayload(
                items = listOf(NotificationSettingDto(key = "a", enabled = true))
            )
        )
        queue.enqueueUpsert(
            entity = SyncEntities.NOTIFICATION_SETTINGS,
            id = "settings-2",
            payload = SyncNotificationSettingsUpsertPayload(
                items = listOf(NotificationSettingDto(key = "b", enabled = false))
            )
        )

        // WHEN
        val pending = dao.listPending(limit = 50)

        // THEN
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.NOTIFICATION_SETTINGS, pending.first().entity)
        assertEquals("settings-2", pending.first().entityId)
    }

    private class NoOpSyncScheduler(context: Context) : SyncScheduler(context) {
        override fun schedule() = Unit
    }
}
