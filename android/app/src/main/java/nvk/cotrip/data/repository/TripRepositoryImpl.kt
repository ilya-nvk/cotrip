package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nvk.cotrip.data.cache.TripsCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UpdateTripRequest
import javax.inject.Inject

class TripRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val tripsCacheStore: TripsCacheStore,
) : TripRepository {

    override val trips: Flow<List<TripDto>> = tripsCacheStore.trips

    override suspend fun refreshTrips(): Result<Unit> {
        return runCatching {
            val trips = api.listTrips().items
            tripsCacheStore.setTrips(trips)
        }
    }

    override suspend fun listTrips(): List<TripDto> {
        val cached = tripsCacheStore.getTrips()
        if (cached.isNotEmpty()) return cached
        val trips = api.listTrips().items
        tripsCacheStore.setTrips(trips)
        return trips
    }

    override suspend fun getTrip(tripId: String): TripDto {
        val trip = api.getTrip(tripId)
        tripsCacheStore.upsertTrip(trip)
        return trip
    }

    override fun observeTrip(tripId: String): Flow<TripDto?> {
        return tripsCacheStore.trips.map { trips ->
            trips.firstOrNull { it.id == tripId }
        }
    }

    override suspend fun createTrip(request: CreateTripRequest): TripDto {
        val trip = api.createTrip(request)
        tripsCacheStore.upsertTrip(trip)
        return trip
    }

    override suspend fun updateTrip(tripId: String, request: UpdateTripRequest): TripDto {
        val trip = api.updateTrip(tripId, request)
        tripsCacheStore.upsertTrip(trip)
        return trip
    }

    override suspend fun archiveTrip(tripId: String) {
        api.archiveTrip(tripId)
        val current = tripsCacheStore.getTrips()
        val updated = current.map { trip ->
            if (trip.id == tripId) trip.copy(status = "archived") else trip
        }
        tripsCacheStore.setTrips(updated)
    }

    override suspend fun deleteTrip(tripId: String) {
        api.deleteTrip(tripId)
        tripsCacheStore.removeTrip(tripId)
    }

    override suspend fun listMembers(tripId: String): List<MemberDto> {
        return api.listMembers(tripId).items
    }

    override suspend fun removeMember(tripId: String, memberId: String) {
        api.removeMember(tripId, memberId)
    }
}
