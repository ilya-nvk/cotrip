package nvk.cotrip.data.cache

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.TripDto

interface TripsCacheStore {
    val trips: Flow<List<TripDto>>

    suspend fun getTrips(): List<TripDto>
    suspend fun setTrips(trips: List<TripDto>)
    suspend fun upsertTrip(trip: TripDto)
    suspend fun removeTrip(tripId: String)
    suspend fun clear()
}
