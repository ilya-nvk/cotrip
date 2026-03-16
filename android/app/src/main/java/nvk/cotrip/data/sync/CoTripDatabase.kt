package nvk.cotrip.data.sync

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SyncChangeEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class CoTripDatabase : RoomDatabase() {
    abstract fun syncChangeDao(): SyncChangeDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_changes_new (
                        queueId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        changeId TEXT NOT NULL,
                        entity TEXT NOT NULL,
                        entityId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        payload TEXT,
                        updatedAt INTEGER NOT NULL,
                        attempts INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO sync_changes_new (changeId, entity, entityId, type, payload, updatedAt, attempts)
                    SELECT id, entity, id, type, payload, updatedAt, attempts
                    FROM sync_changes
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE sync_changes")
                db.execSQL("ALTER TABLE sync_changes_new RENAME TO sync_changes")
            }
        }
    }
}
