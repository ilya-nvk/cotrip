package nvk.cotrip.data.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.SyncChangesResponse
import nvk.cotrip.data.network.dto.SyncConflict
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
class SyncWorkerTest {
    private lateinit var context: Context
    private lateinit var database: CoTripDatabase
    private lateinit var dao: SyncChangeDao
    private lateinit var queue: SyncQueueRepository
    private lateinit var api: CoTripApi
    private lateinit var networkStateProvider: NetworkStateProvider
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CoTripDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.syncChangeDao()
        queue = SyncQueueRepository(
            database = database,
            scheduler = NoOpSyncScheduler(context),
            json = json,
        )
        api = mockk()
        networkStateProvider = mockk()
        every { networkStateProvider.isOnline() } returns true
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun given_pendingChanges_when_doWorkReturnsAppliedAndConflicts_then_removesAppliedAndNonRetryableKeepsRetryable() = runTest {
        // GIVEN
        insertChange(changeId = "c-applied", entityId = "entity-1")
        insertChange(changeId = "c-non-retry", entityId = "entity-2")
        insertChange(changeId = "c-retry", entityId = "entity-3")

        coEvery { api.postSyncChanges(any()) } returns SyncChangesResponse(
            applied = listOf("c-applied"),
            conflicts = listOf(
                SyncConflict(
                    changeId = "c-non-retry",
                    entityId = "entity-2",
                    reason = "invalid_payload",
                    retryable = false,
                ),
                SyncConflict(
                    changeId = "c-retry",
                    entityId = "entity-3",
                    reason = "dependency_not_ready",
                    retryable = true,
                ),
            ),
        )

        // WHEN
        val worker = createWorker()
        val result = worker.doWork()

        // THEN
        assertTrue(result is ListenableWorker.Result.Success)
        val pending = dao.listPending(10)
        assertEquals(1, pending.size)
        assertEquals("c-retry", pending.first().changeId)
        assertEquals(1, pending.first().attempts)
    }

    @Test
    fun given_twoChangesSameEntity_when_nonRetryableConflictByChangeId_then_keepsSiblingForSameEntity() = runTest {
        // GIVEN
        queue.enqueueCreate(
            entity = SyncEntities.TRIP,
            id = "same-entity",
            payload = mapOf("title" to "created"),
        )
        queue.enqueueUpsert(
            entity = SyncEntities.TRIP,
            id = "same-entity",
            payload = mapOf("title" to "updated"),
        )
        val beforeSync = dao.listPending(10)
        assertEquals(2, beforeSync.size)
        val createChange = beforeSync.first { it.type == "create" }
        val upsertChange = beforeSync.first { it.type == "upsert" }

        coEvery { api.postSyncChanges(any()) } returns SyncChangesResponse(
            applied = emptyList(),
            conflicts = listOf(
                SyncConflict(
                    changeId = upsertChange.changeId,
                    entityId = upsertChange.entityId,
                    reason = "invalid_payload",
                    retryable = false,
                ),
            ),
        )

        // WHEN
        val worker = createWorker()
        val result = worker.doWork()

        // THEN
        assertTrue(result is ListenableWorker.Result.Success)
        val pending = dao.listPending(10)
        assertEquals(1, pending.size)
        assertEquals(createChange.changeId, pending.first().changeId)
        assertEquals(1, pending.first().attempts)
    }

    @Test
    fun given_legacyAppliedByEntityIdFallback_when_doWork_then_deletesUniqueEntityOperation() = runTest {
        // GIVEN
        insertChange(changeId = "c-trip", entityId = "trip-1")

        coEvery { api.postSyncChanges(any()) } returns SyncChangesResponse(
            applied = listOf("trip-1"),
            conflicts = emptyList(),
        )

        // WHEN
        val worker = createWorker()
        val result = worker.doWork()

        // THEN
        assertTrue(result is ListenableWorker.Result.Success)
        val pending = dao.listPending(10)
        assertTrue(pending.isEmpty())
    }

    private suspend fun insertChange(
        changeId: String,
        entityId: String,
        type: String = "upsert",
    ) {
        dao.insert(
            SyncChangeEntity(
                changeId = changeId,
                entity = SyncEntities.TRIP,
                entityId = entityId,
                type = type,
                payload = null,
                updatedAt = System.currentTimeMillis(),
                attempts = 0,
            ),
        )
    }

    private fun createWorker(): SyncWorker {
        return SyncWorker(
            context = context,
            params = mockk<WorkerParameters>(relaxed = true),
            database = database,
            api = api,
            json = json,
            networkStateProvider = networkStateProvider,
        )
    }

    private class NoOpSyncScheduler(context: Context) : SyncScheduler(context) {
        override fun schedule() = Unit
    }
}
