package nvk.cotrip.data.repository

import java.io.IOException
import javax.inject.Inject
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MoveActivityRequest
import nvk.cotrip.data.network.dto.ReorderActivitiesRequest
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.UpdateActivityRequest
import nvk.cotrip.data.network.dto.UpdateDayRequest
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository

class ItineraryRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
) : ItineraryRepository {
    override suspend fun getItinerary(tripId: String): List<ItineraryDayDto> {
        return api.getItinerary(tripId).items
    }

    override suspend fun refreshItinerary(tripId: String): List<ItineraryDayDto> {
        return getItinerary(tripId)
    }

    override suspend fun updateDay(dayId: String, request: UpdateDayRequest) {
        try {
            api.updateDay(dayId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.DAY, dayId, request)
        }
    }

    override suspend fun createActivity(dayId: String, request: CreateActivityRequest): ActivityDto {
        return api.createActivity(dayId, request)
    }

    override suspend fun updateActivity(activityId: String, request: UpdateActivityRequest) {
        try {
            api.updateActivity(activityId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.ACTIVITY, activityId, request)
        }
    }

    override suspend fun moveActivity(activityId: String, request: MoveActivityRequest) {
        try {
            api.moveActivity(activityId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.ACTIVITY, activityId, request)
        }
    }

    override suspend fun deleteActivity(activityId: String) {
        try {
            api.deleteActivity(activityId)
        } catch (e: IOException) {
            syncQueueRepository.enqueueDelete(SyncEntities.ACTIVITY, activityId)
        }
    }

    override suspend fun reorderActivities(dayId: String, orderedIds: List<String>) {
        api.reorderActivities(dayId, ReorderActivitiesRequest(orderedIds))
    }

    override suspend fun trimOutOfRange(tripId: String, request: TrimOutOfRangeRequest) {
        api.trimOutOfRangeDays(tripId, request)
    }
}
