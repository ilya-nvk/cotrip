package nvk.cotrip.data.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_changes")
data class SyncChangeEntity(
    @PrimaryKey(autoGenerate = true) val queueId: Long = 0,
    val changeId: String,
    val entity: String,
    val entityId: String,
    val type: String,
    val payload: String?,
    val updatedAt: Long,
    val attempts: Int,
)
