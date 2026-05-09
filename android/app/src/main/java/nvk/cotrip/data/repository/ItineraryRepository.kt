package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CitySuggestionDto
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MoveActivityRequest
import nvk.cotrip.data.network.dto.PlaceSuggestionDto
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.UpdateActivityRequest
import nvk.cotrip.data.network.dto.UpdateDayRequest

interface ItineraryRepository {
    fun observeItinerary(tripId: String): Flow<List<ItineraryDayDto>>
    fun getItinerary(tripId: String): Flow<List<ItineraryDayDto>>
    suspend fun refreshItinerary(tripId: String): Result<Unit>
    suspend fun searchCities(tripId: String, query: String, limit: Int = 8): List<CitySuggestionDto>
    suspend fun searchPlaces(tripId: String, query: String, limit: Int = 8): List<PlaceSuggestionDto>
    suspend fun updateDay(dayId: String, request: UpdateDayRequest)
    suspend fun updateDaysCity(tripId: String, dayIds: List<String>, request: UpdateDayRequest)
    suspend fun createActivity(dayId: String, request: CreateActivityRequest): ActivityDto
    suspend fun updateActivity(activityId: String, request: UpdateActivityRequest)
    suspend fun moveActivity(activityId: String, request: MoveActivityRequest)
    suspend fun deleteActivity(activityId: String)
    suspend fun reorderActivities(dayId: String, orderedIds: List<String>)
    suspend fun trimOutOfRange(tripId: String, request: TrimOutOfRangeRequest)
}
