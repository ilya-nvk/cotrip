package nvk.cotrip.data.sync

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

class SyncQueueRepository @Inject constructor(
    database: CoTripDatabase,
    private val scheduler: SyncScheduler,
    @PublishedApi internal val json: Json,
) {
    private val dao: SyncChangeDao = database.syncChangeDao()
    suspend fun enqueueDelete(entity: String, id: String) {
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
        dao.upsert(
            SyncChangeEntity(
                id = id,
                entity = entity,
                type = type,
                payload = rawPayload,
                updatedAt = System.currentTimeMillis(),
                attempts = 0,
            )
        )
        scheduler.schedule()
    }
}
