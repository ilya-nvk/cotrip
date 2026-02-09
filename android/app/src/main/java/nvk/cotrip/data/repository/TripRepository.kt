package nvk.cotrip.data.repository

import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UserDto
import javax.inject.Inject

class TripRepository @Inject constructor(
    private val api: CoTripApi,
) {
    suspend fun listTrips(status: String? = null): List<TripDto> {
        return api.listTrips(status).items
    }

    suspend fun getTrip(tripId: String): TripDto {
        return api.getTrip(tripId)
    }

    suspend fun listMembers(tripId: String): List<MemberDto> {
        return api.listMembers(tripId).items
    }

    suspend fun getMe(): UserDto {
        return api.getMe()
    }
}
