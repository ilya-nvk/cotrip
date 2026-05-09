package nvk.cotrip.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface SyncChangeDao {
    @Query("DELETE FROM sync_changes")
    suspend fun deleteAll()

    @Insert
    suspend fun insert(change: SyncChangeEntity): Long

    @Query("SELECT * FROM sync_changes ORDER BY updatedAt ASC LIMIT :limit")
    suspend fun listPending(limit: Int): List<SyncChangeEntity>

    @Query("DELETE FROM sync_changes WHERE queueId IN (:queueIds)")
    suspend fun deleteByQueueIds(queueIds: List<Long>)

    @Query("UPDATE sync_changes SET attempts = attempts + 1 WHERE queueId IN (:queueIds)")
    suspend fun bumpAttempts(queueIds: List<Long>)

    @Query("DELETE FROM sync_changes WHERE entity = :entity AND entityId = :entityId")
    suspend fun deleteByEntity(entity: String, entityId: String)

    @Query("DELETE FROM sync_changes WHERE entity = :entity")
    suspend fun deleteAllByEntity(entity: String)

    @Query("DELETE FROM sync_changes WHERE entity = :entity AND type = :type")
    suspend fun deleteByEntityAndType(entity: String, type: String)

    @Query("DELETE FROM sync_changes WHERE entity = :entity AND entityId = :entityId AND type = :type")
    suspend fun deleteByEntityAndEntityIdAndType(entity: String, entityId: String, type: String)

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM sync_changes
            WHERE entity = :entity
              AND entityId = :entityId
              AND type = :type
            LIMIT 1
        )
        """
    )
    suspend fun hasPendingType(entity: String, entityId: String, type: String): Boolean
}
