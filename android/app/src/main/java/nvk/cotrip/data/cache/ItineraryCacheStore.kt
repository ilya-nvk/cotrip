package nvk.cotrip.data.cache

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.ItineraryDayDto

interface ItineraryCacheStore {
    fun observeItinerary(tripId: String): Flow<List<ItineraryDayDto>>
    suspend fun getItinerary(tripId: String): List<ItineraryDayDto>
    suspend fun getAll(): Map<String, List<ItineraryDayDto>>
    suspend fun setItinerary(tripId: String, days: List<ItineraryDayDto>)
    suspend fun updateItinerary(tripId: String, transform: (List<ItineraryDayDto>) -> List<ItineraryDayDto>)
    suspend fun clearTrip(tripId: String)
}
