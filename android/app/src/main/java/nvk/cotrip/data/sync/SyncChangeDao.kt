package nvk.cotrip.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncChangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(change: SyncChangeEntity)

    @Query("SELECT * FROM sync_changes ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun listPending(limit: Int): List<SyncChangeEntity>

    @Query("DELETE FROM sync_changes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("UPDATE sync_changes SET attempts = attempts + 1 WHERE id IN (:ids)")
    suspend fun bumpAttempts(ids: List<String>)
}
