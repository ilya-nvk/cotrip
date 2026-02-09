package nvk.cotrip.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.SyncChangeItem
import nvk.cotrip.data.network.dto.SyncChangesRequest
import java.io.IOException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    database: CoTripDatabase,
    private val api: CoTripApi,
    private val json: Json,
    private val networkStateProvider: NetworkStateProvider,
) : CoroutineWorker(context, params) {
    private val dao: SyncChangeDao = database.syncChangeDao()
    override suspend fun doWork(): Result {
        if (!networkStateProvider.isOnline()) {
            return Result.retry()
        }

        val pending = dao.listPending(50)
        if (pending.isEmpty()) return Result.success()

        val items = pending.mapNotNull { change ->
            val payload = change.payload?.let { json.decodeFromString<JsonObject>(it) }
            SyncChangeItem(
                entity = change.entity,
                id = change.id,
                type = change.type,
                payload = payload
            )
        }

        return try {
            val response = api.postSyncChanges(SyncChangesRequest(items))
            val applied = response.applied.toSet()
            val conflicts = response.conflicts.map { it.id }.toSet()
            val toDelete = pending.map { it.id }
                .filter { it in applied || it in conflicts }

            if (toDelete.isNotEmpty()) {
                dao.deleteByIds(toDelete)
            }

            val remaining = pending.map { it.id }.filterNot { it in toDelete }
            if (remaining.isNotEmpty()) {
                dao.bumpAttempts(remaining)
            }

            if (pending.size >= 50 && remaining.isNotEmpty()) Result.retry() else Result.success()
        } catch (e: IOException) {
            dao.bumpAttempts(pending.map { it.id })
            Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
