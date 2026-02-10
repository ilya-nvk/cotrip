package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nvk.cotrip.data.cache.TripsCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UpdateTripRequest
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import java.io.IOException
import javax.inject.Inject
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException

class TripRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val tripsCacheStore: TripsCacheStore,
    private val syncQueueRepository: SyncQueueRepository,
    private val networkStateProvider: NetworkStateProvider,
) : TripRepository {

    private companion object {
        private const val TAG = "TripRepository"
    }

    override val trips: Flow<List<TripDto>> = tripsCacheStore.trips

    override suspend fun refreshTrips(): Result<Unit> {
        return try {
            val trips = api.listTrips().items
            safeLocalMutation("refreshTrips.setTrips") {
                tripsCacheStore.setTrips(trips)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listTrips(): List<TripDto> {
        if (!networkStateProvider.isOnline()) {
            return runCatching { tripsCacheStore.getTrips() }
                .onFailure { AppLogger.w(TAG, "listTrips offline cache read failed", it) }
                .getOrDefault(emptyList())
        }

        val trips = api.listTrips().items
        safeLocalMutation("listTrips.setTrips") {
            tripsCacheStore.setTrips(trips)
        }
        return trips
    }

    override suspend fun getTrip(tripId: String): TripDto {
        val trip = api.getTrip(tripId)
        safeLocalMutation("getTrip.upsertTrip(tripId=$tripId)") {
            tripsCacheStore.upsertTrip(trip)
        }
        return trip
    }

    override fun observeTrip(tripId: String): Flow<TripDto?> {
        return tripsCacheStore.trips.map { trips ->
            trips.firstOrNull { it.id == tripId }
        }
    }

    override suspend fun createTrip(request: CreateTripRequest): TripDto {
        val trip = api.createTrip(request)
        safeLocalMutation("createTrip.upsertTrip(tripId=${trip.id})") {
            tripsCacheStore.upsertTrip(trip)
        }
        return trip
    }

    override suspend fun updateTrip(tripId: String, request: UpdateTripRequest): TripDto {
        return try {
            val trip = api.updateTrip(tripId, request)
            safeLocalMutation("updateTrip.upsertTrip(tripId=$tripId)") {
                tripsCacheStore.upsertTrip(trip)
            }
            trip
        } catch (e: IOException) {
            val local = runCatching { tripsCacheStore.getTrips().firstOrNull { it.id == tripId } }
                .onFailure { AppLogger.w(TAG, "updateTrip cache read failed for $tripId", it) }
                .getOrNull()
            syncQueueRepository.enqueueUpsert(SyncEntities.TRIP, tripId, request)
            if (local == null) throw e
            val updatedLocal = local.copy(
                title = request.title ?: local.title,
                description = request.description ?: local.description,
                startDate = request.startDate ?: local.startDate,
                endDate = request.endDate ?: local.endDate,
                locationLine = request.locationLine ?: local.locationLine,
                coverUrl = request.coverUrl ?: local.coverUrl,
                currencyCode = request.currencyCode ?: local.currencyCode,
            )
            safeLocalMutation("updateTrip.offlineUpsert(tripId=$tripId)") {
                tripsCacheStore.upsertTrip(updatedLocal)
            }
            updatedLocal
        }
    }

    override suspend fun archiveTrip(tripId: String) {
        try {
            api.archiveTrip(tripId).requireSuccess()
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(
                entity = SyncEntities.TRIP,
                id = tripId,
                payload = mapOf("status" to "archived"),
            )
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            AppLogger.i(TAG, "archiveTrip got 404 for tripId=$tripId, treating as already archived")
        }
        val cached = runCatching { tripsCacheStore.getTrips() }
            .onFailure { AppLogger.w(TAG, "archiveTrip cache read failed for $tripId", it) }
            .getOrDefault(emptyList())
        val updated = cached.map { trip ->
            if (trip.id == tripId) trip.copy(status = "archived") else trip
        }
        safeLocalMutation("archiveTrip.setTrips(tripId=$tripId)") {
            tripsCacheStore.setTrips(updated)
        }
    }

    override suspend fun deleteTrip(tripId: String) {
        try {
            api.deleteTrip(tripId).requireSuccess()
        } catch (e: IOException) {
            syncQueueRepository.enqueueDelete(SyncEntities.TRIP, tripId)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            AppLogger.i(TAG, "deleteTrip got 404 for tripId=$tripId, treating as already deleted")
        }
        safeLocalMutation("deleteTrip.removeTrip(tripId=$tripId)") {
            tripsCacheStore.removeTrip(tripId)
        }
    }

    override suspend fun listMembers(tripId: String): List<MemberDto> {
        return api.listMembers(tripId).items
    }

    override suspend fun removeMember(tripId: String, memberId: String) {
        api.removeMember(tripId, memberId).requireSuccess()
    }
}
