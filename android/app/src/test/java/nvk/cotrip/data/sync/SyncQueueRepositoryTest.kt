package nvk.cotrip.data.sync

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
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
    fun enqueueDelete_afterPendingCreate_compactsQueue() = runTest {
        queue.enqueueCreate(
            entity = SyncEntities.IDEA,
            id = "idea-1",
            payload = mapOf("title" to "draft")
        )
        queue.enqueueDelete(entity = SyncEntities.IDEA, id = "idea-1")

        val pending = dao.listPending(limit = 50)
        assertTrue(pending.isEmpty())
    }

    @Test
    fun enqueueCreateThenUpsert_keepsBothOperations() = runTest {
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

        val pending = dao.listPending(limit = 50)
        assertEquals(2, pending.size)
        assertEquals(listOf("create", "upsert"), pending.map { it.type })
        assertTrue(pending.all { it.entityId == "idea-2" })
    }

    @Test
    fun migration1To2_preservesPendingRows() = runTest {
        val dbName = "sync_migration_${System.currentTimeMillis()}.db"
        val file = context.getDatabasePath(dbName)
        if (file.exists()) {
            file.delete()
        }

        SQLiteDatabase.openOrCreateDatabase(file, null).use { rawDb ->
            rawDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sync_changes (
                    id TEXT NOT NULL PRIMARY KEY,
                    entity TEXT NOT NULL,
                    type TEXT NOT NULL,
                    payload TEXT,
                    updatedAt INTEGER NOT NULL,
                    attempts INTEGER NOT NULL
                )
                """.trimIndent()
            )
            rawDb.execSQL(
                """
                INSERT INTO sync_changes (id, entity, type, payload, updatedAt, attempts)
                VALUES ('legacy-id', 'trip', 'upsert', '{"title":"legacy"}', 1, 0)
                """.trimIndent()
            )
            rawDb.execSQL("PRAGMA user_version = 1")
        }

        val migratedDb = Room.databaseBuilder(context, CoTripDatabase::class.java, dbName)
            .addMigrations(CoTripDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val migratedRows = migratedDb.syncChangeDao().listPending(limit = 10)
            assertEquals(1, migratedRows.size)
            val row = migratedRows.first()
            assertEquals("legacy-id", row.changeId)
            assertEquals("legacy-id", row.entityId)
            assertEquals("trip", row.entity)
        } finally {
            migratedDb.close()
        }

        file.delete()
    }

    private class NoOpSyncScheduler(context: Context) : SyncScheduler(context) {
        override fun schedule() = Unit
    }
}
