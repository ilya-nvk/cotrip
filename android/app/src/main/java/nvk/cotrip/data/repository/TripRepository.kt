package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UpdateTripRequest
import nvk.cotrip.data.network.dto.CreateTripRequest

interface TripRepository {
    val trips: Flow<List<TripDto>>

    suspend fun refreshTrips(): Result<Unit>
    suspend fun listTrips(): List<TripDto>
    suspend fun getTrip(tripId: String): TripDto
    fun observeTrip(tripId: String): Flow<TripDto?>

    suspend fun createTrip(request: CreateTripRequest): TripDto
    suspend fun updateTrip(tripId: String, request: UpdateTripRequest): TripDto
    suspend fun archiveTrip(tripId: String)
    suspend fun deleteTrip(tripId: String)

    suspend fun listMembers(tripId: String): List<MemberDto>
    suspend fun removeMember(tripId: String, memberId: String)
}
