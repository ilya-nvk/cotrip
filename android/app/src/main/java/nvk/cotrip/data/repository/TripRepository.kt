package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UpdateTripRequest

interface TripRepository {
    val trips: Flow<List<TripDto>>
    suspend fun getTrip(tripId: String): Flow<TripDto>

    suspend fun refreshTrips(): Result<Unit>

    suspend fun createTrip(request: CreateTripRequest): String
    suspend fun updateTrip(tripId: String, request: UpdateTripRequest): Result<Unit>
    suspend fun archiveTrip(tripId: String)
    suspend fun deleteTrip(tripId: String)

    suspend fun tripMembers(tripId: String): Flow<List<MemberDto>>
    suspend fun removeMember(tripId: String, memberId: String)
}
