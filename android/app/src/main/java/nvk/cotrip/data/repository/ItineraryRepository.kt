package nvk.cotrip.data.repository

import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MoveActivityRequest
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.UpdateActivityRequest
import nvk.cotrip.data.network.dto.UpdateDayRequest
import javax.inject.Inject

class ItineraryRepository @Inject constructor(
    private val api: CoTripApi,
) {
    suspend fun getItinerary(tripId: String): List<ItineraryDayDto> {
        return api.getItinerary(tripId).items
    }

    suspend fun refreshItinerary(tripId: String): List<ItineraryDayDto> {
        return getItinerary(tripId)
    }

    suspend fun updateDay(dayId: String, request: UpdateDayRequest) {
        api.updateDay(dayId, request)
    }

    suspend fun createActivity(dayId: String, request: CreateActivityRequest): ActivityDto {
        return api.createActivity(dayId, request)
    }

    suspend fun updateActivity(activityId: String, request: UpdateActivityRequest): ActivityDto {
        return api.updateActivity(activityId, request)
    }

    suspend fun moveActivity(activityId: String, request: MoveActivityRequest): ActivityDto {
        return api.moveActivity(activityId, request)
    }

    suspend fun deleteActivity(activityId: String) {
        api.deleteActivity(activityId)
    }

    suspend fun trimOutOfRange(tripId: String, request: TrimOutOfRangeRequest) {
        api.trimOutOfRangeDays(tripId, request)
    }
}
