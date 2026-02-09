package nvk.cotrip.data.repository

import java.io.IOException
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MoveActivityRequest
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.UpdateActivityRequest
import nvk.cotrip.data.network.dto.UpdateDayRequest
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import javax.inject.Inject

class ItineraryRepository @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
) {
    suspend fun getItinerary(tripId: String): List<ItineraryDayDto> {
        return api.getItinerary(tripId).items
    }

    suspend fun refreshItinerary(tripId: String): List<ItineraryDayDto> {
        return getItinerary(tripId)
    }

    suspend fun updateDay(dayId: String, request: UpdateDayRequest) {
        try {
            api.updateDay(dayId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.DAY, dayId, request)
        }
    }

    suspend fun createActivity(dayId: String, request: CreateActivityRequest): ActivityDto {
        return api.createActivity(dayId, request)
    }

    suspend fun updateActivity(activityId: String, request: UpdateActivityRequest) {
        try {
            api.updateActivity(activityId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.ACTIVITY, activityId, request)
        }
    }

    suspend fun moveActivity(activityId: String, request: MoveActivityRequest) {
        try {
            api.moveActivity(activityId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.ACTIVITY, activityId, request)
        }
    }

    suspend fun deleteActivity(activityId: String) {
        try {
            api.deleteActivity(activityId)
        } catch (e: IOException) {
            syncQueueRepository.enqueueDelete(SyncEntities.ACTIVITY, activityId)
        }
    }

    suspend fun trimOutOfRange(tripId: String, request: TrimOutOfRangeRequest) {
        api.trimOutOfRangeDays(tripId, request)
    }
}
