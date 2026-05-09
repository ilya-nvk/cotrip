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

    companion object {
        @PublishedApi
        internal const val TYPE_CREATE = "create"
        @PublishedApi
        internal const val TYPE_UPSERT = "upsert"
        @PublishedApi
        internal val SINGLETON_COMMAND_UPSERT_ENTITIES = setOf(
            SyncEntities.IDEA_STATUS,
            SyncEntities.ACTIVITY_REORDER,
            SyncEntities.ITINERARY_TRIM,
            SyncEntities.NOTIFICATION_SETTINGS,
            SyncEntities.NOTIFICATION_READ,
            SyncEntities.USER_PROFILE,
            SyncEntities.AI_SUGGESTION_SAVE,
        )
    }

    internal suspend inline fun <reified T> enqueueCreate(entity: String, id: String, payload: T) {
        val jsonPayload = json.encodeToJsonElement(payload).jsonObject
        enqueue(entity = entity, id = id, type = "create", payload = jsonPayload)
    }

    suspend fun enqueueDelete(entity: String, id: String) {
        enqueueDeleteInternal(entity = entity, id = id, payload = null)
    }

    internal suspend inline fun <reified T> enqueueDelete(entity: String, id: String, payload: T) {
        val jsonPayload = json.encodeToJsonElement(payload).jsonObject
        enqueueDeleteInternal(entity = entity, id = id, payload = jsonPayload)
    }

    private suspend fun enqueueDeleteInternal(entity: String, id: String, payload: JsonObject?) {
        val hasPendingCreate = dao.hasPendingType(entity = entity, entityId = id, type = TYPE_CREATE)
        if (hasPendingCreate) {
            dao.deleteByEntity(entity = entity, entityId = id)
            return
        }
        enqueue(entity = entity, id = id, type = "delete", payload = payload)
    }

    internal suspend inline fun <reified T> enqueueUpsert(entity: String, id: String, payload: T) {
        val jsonPayload = json.encodeToJsonElement(payload).jsonObject
        if (entity in SINGLETON_COMMAND_UPSERT_ENTITIES) {
            dao.deleteByEntityAndType(entity = entity, type = TYPE_UPSERT)
        } else {
            dao.deleteByEntityAndEntityIdAndType(
                entity = entity,
                entityId = id,
                type = TYPE_UPSERT,
            )
        }
        enqueue(entity = entity, id = id, type = "upsert", payload = jsonPayload)
    }

    suspend fun clearAllPendingChanges() {
        dao.deleteAll()
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
