package nvk.cotrip.data.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.UUID
import javax.inject.Inject

class SyncQueueRepository @Inject constructor(
    database: CoTripDatabase,
    private val scheduler: SyncScheduler,
    @PublishedApi internal val json: Json,
) {
    private val dao: SyncChangeDao = database.syncChangeDao()

    internal suspend inline fun <reified T> enqueueCreate(entity: String, id: String, payload: T) {
        val jsonPayload = json.encodeToJsonElement(payload).jsonObject
        enqueue(entity = entity, id = id, type = "create", payload = jsonPayload)
    }

    suspend fun enqueueDelete(entity: String, id: String) {
        val hasPendingCreate = dao.hasPendingType(entity = entity, entityId = id, type = "create")
        if (hasPendingCreate) {
            dao.deleteByEntity(entity = entity, entityId = id)
            return
        }
        enqueue(entity = entity, id = id, type = "delete", payload = null)
    }

    internal suspend inline fun <reified T> enqueueUpsert(entity: String, id: String, payload: T) {
        val jsonPayload = json.encodeToJsonElement(payload).jsonObject
        enqueue(entity = entity, id = id, type = "upsert", payload = jsonPayload)
    }

    suspend fun enqueue(
        entity: String,
        id: String,
        type: String,
        payload: JsonObject?,
    ) {
        val rawPayload = payload?.let { json.encodeToString(it) }
        dao.insert(
            SyncChangeEntity(
                changeId = UUID.randomUUID().toString(),
                entity = entity,
                entityId = id,
                type = type,
                payload = rawPayload,
                updatedAt = System.currentTimeMillis(),
                attempts = 0,
            )
        )
        scheduler.schedule()
    }
}
