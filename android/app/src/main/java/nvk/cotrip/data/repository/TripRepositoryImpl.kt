package nvk.cotrip.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import nvk.cotrip.data.cache.TripMembersCacheStore
import nvk.cotrip.data.cache.TripsCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UpdateTripRequest
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class TripRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val tripsCacheStore: TripsCacheStore,
    private val tripMembersCacheStore: TripMembersCacheStore,
    private val syncQueueRepository: SyncQueueRepository,
    private val networkStateProvider: NetworkStateProvider,
) : TripRepository {

    private companion object {
        private const val TAG = "TripRepository"
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    override fun getTrip(tripId: String): Flow<TripDto> {
        if (networkStateProvider.isOnline()) {
            ioScope.launch {
                runCatching {
                    val trip = api.getTrip(tripId)
                    safeLocalMutation("getTrip.upsertTrip(tripId=$tripId)") {
                        tripsCacheStore.upsertTrip(trip)
                    }
                }.onFailure { error ->
                    AppLogger.w(TAG, "getTrip network refresh failed for $tripId", error)
                }
            }
        }
        return tripsCacheStore.trips.mapNotNull { trips ->
            trips.firstOrNull { it.id == tripId }
        }
    }

    override suspend fun createTrip(request: CreateTripRequest): String {
        val trip = api.createTrip(request)
        safeLocalMutation("createTrip.upsertTrip(tripId=${trip.id})") {
            tripsCacheStore.upsertTrip(trip)
        }
        return trip.id
    }

    override suspend fun updateTrip(tripId: String, request: UpdateTripRequest): Result<Unit> {
        return try {
            val trip = api.updateTrip(tripId, request)
            safeLocalMutation("updateTrip.upsertTrip(tripId=$tripId)") {
                tripsCacheStore.upsertTrip(trip)
            }
            Result.success(Unit)
        } catch (e: IOException) {
            val local = runCatching { tripsCacheStore.getTrips().firstOrNull { it.id == tripId } }
                .onFailure { AppLogger.w(TAG, "updateTrip cache read failed for $tripId", it) }
                .getOrNull()
            syncQueueRepository.enqueueUpsert(SyncEntities.TRIP, tripId, request)
            if (local != null) {
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
            }
            Result.failure(e)
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

    override fun tripMembers(tripId: String): Flow<List<MemberDto>> {
        // Return cached flow immediately; refresh in background.
        if (networkStateProvider.isOnline()) {
            ioScope.launch {
                runCatching { api.listMembers(tripId).items }
                    .onSuccess { members ->
                        safeLocalMutation("tripMembers.setMembers(tripId=$tripId)") {
                            tripMembersCacheStore.setMembers(tripId, members)
                        }
                    }
                    .onFailure { error ->
                        AppLogger.w(TAG, "tripMembers network refresh failed for $tripId", error)
                    }
            }
        }
        return tripMembersCacheStore.observeMembers(tripId)
    }

    override suspend fun removeMember(tripId: String, memberId: String) {
        api.removeMember(tripId, memberId).requireSuccess()
        safeLocalMutation("removeMember.removeMember(tripId=$tripId, memberId=$memberId)") {
            tripMembersCacheStore.removeMember(tripId, memberId)
        }
    }
}
