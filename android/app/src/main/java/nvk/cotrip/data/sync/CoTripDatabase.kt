package nvk.cotrip.data.sync

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SyncChangeEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class CoTripDatabase : RoomDatabase() {
    abstract fun syncChangeDao(): SyncChangeDao
}
