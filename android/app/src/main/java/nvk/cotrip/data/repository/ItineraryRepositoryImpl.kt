package nvk.cotrip.data.repository

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.cache.ItineraryCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MoveActivityRequest
import nvk.cotrip.data.network.dto.ReorderActivitiesRequest
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.UpdateActivityRequest
import nvk.cotrip.data.network.dto.UpdateDayRequest
import nvk.cotrip.data.network.dto.CitySuggestionDto
import nvk.cotrip.data.network.dto.PlaceSuggestionDto
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException

class ItineraryRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
    private val itineraryCacheStore: ItineraryCacheStore,
) : ItineraryRepository {
    private companion object {
        private const val TAG = "ItineraryRepository"
    }

    override fun observeItinerary(tripId: String): Flow<List<ItineraryDayDto>> {
        return itineraryCacheStore.observeItinerary(tripId)
    }

    override suspend fun getItinerary(tripId: String): List<ItineraryDayDto> {
        val cached = itineraryCacheStore.getItinerary(tripId)
        if (cached.isNotEmpty()) return cached
        return refreshItinerary(tripId)
    }

    override suspend fun refreshItinerary(tripId: String): List<ItineraryDayDto> {
        val itinerary = api.getItinerary(tripId).items
        safeLocalMutation("refreshItinerary.setItinerary(tripId=$tripId)") {
            itineraryCacheStore.setItinerary(tripId, itinerary)
        }
        return itinerary
    }

    override suspend fun searchCities(tripId: String, query: String, limit: Int): List<CitySuggestionDto> {
        return api.searchCities(tripId = tripId, query = query, limit = limit).items
    }

    override suspend fun searchPlaces(tripId: String, query: String, limit: Int): List<PlaceSuggestionDto> {
        return api.searchPlaces(tripId = tripId, query = query, limit = limit).items
    }

    override suspend fun updateDay(dayId: String, request: UpdateDayRequest) {
        try {
            api.updateDay(dayId, request).requireSuccess()
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.DAY, dayId, request)
            return
        }

        safeLocalMutation("updateDay.updateItinerary(dayId=$dayId)") {
            val tripId = findTripIdForDay(dayId) ?: return@safeLocalMutation
            itineraryCacheStore.updateItinerary(tripId) { days ->
                days.map { day ->
                    if (day.id == dayId) {
                        day.copy(
                            city = request.city,
                            cityProviderId = request.cityProviderId,
                            cityLat = request.cityLat,
                            cityLon = request.cityLon,
                        )
                    } else {
                        day
                    }
                }
            }
        }
    }

    override suspend fun createActivity(dayId: String, request: CreateActivityRequest): ActivityDto {
        val activity = api.createActivity(dayId, request)
        safeLocalMutation("createActivity.updateItinerary(dayId=$dayId, activityId=${activity.id})") {
            val tripId = findTripIdForDay(dayId) ?: return@safeLocalMutation
            itineraryCacheStore.updateItinerary(tripId) { days ->
                days.map { day ->
                    if (day.id == dayId) {
                        day.copy(activities = (day.activities + activity).sortedBy { it.orderIndex })
                    } else day
                }
            }
        }
        return activity
    }

    override suspend fun updateActivity(activityId: String, request: UpdateActivityRequest) {
        val updated = try {
            api.updateActivity(activityId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.ACTIVITY, activityId, request)
            return
        }
        safeLocalMutation("updateActivity.updateItinerary(activityId=$activityId)") {
            val tripId = findTripIdForDay(updated.dayId) ?: return@safeLocalMutation
            itineraryCacheStore.updateItinerary(tripId) { days ->
                days.map { day ->
                    if (day.id == updated.dayId) {
                        val updatedActivities = day.activities.map { activity ->
                            if (activity.id == updated.id) updated else activity
                        }
                        day.copy(activities = updatedActivities)
                    } else {
                        day
                    }
                }
            }
        }
    }

    override suspend fun moveActivity(activityId: String, request: MoveActivityRequest) {
        val moved = try {
            api.moveActivity(activityId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.ACTIVITY, activityId, request)
            return
        }
        safeLocalMutation("moveActivity.updateItinerary(activityId=$activityId)") {
            val tripId = findTripIdForDay(request.dayId) ?: return@safeLocalMutation
            itineraryCacheStore.updateItinerary(tripId) { days ->
                val without = days.map { day ->
                    day.copy(activities = day.activities.filterNot { it.id == activityId })
                }
                without.map { day ->
                    if (day.id == moved.dayId) {
                        day.copy(activities = (day.activities + moved).sortedBy { it.orderIndex })
                    } else day
                }
            }
        }
    }

    override suspend fun deleteActivity(activityId: String) {
        val lookup = runCatching { findTripAndDayForActivity(activityId) }
            .onFailure { AppLogger.w(TAG, "deleteActivity lookup failed for activityId=$activityId", it) }
            .getOrNull()
        try {
            api.deleteActivity(activityId).requireSuccess()
        } catch (e: IOException) {
            syncQueueRepository.enqueueDelete(SyncEntities.ACTIVITY, activityId)
            return
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            AppLogger.i(TAG, "deleteActivity got 404 for activityId=$activityId, treating as already deleted")
        }
        if (lookup != null) {
            safeLocalMutation("deleteActivity.updateItinerary(activityId=$activityId)") {
                itineraryCacheStore.updateItinerary(lookup.first) { days ->
                    days.map { day ->
                        if (day.id == lookup.second) {
                            day.copy(activities = day.activities.filterNot { it.id == activityId })
                        } else {
                            day
                        }
                    }
                }
            }
        }
    }

    override suspend fun reorderActivities(dayId: String, orderedIds: List<String>) {
        api.reorderActivities(dayId, ReorderActivitiesRequest(orderedIds)).requireSuccess()
        safeLocalMutation("reorderActivities.updateItinerary(dayId=$dayId)") {
            val tripId = findTripIdForDay(dayId) ?: return@safeLocalMutation
            itineraryCacheStore.updateItinerary(tripId) { days ->
                days.map { day ->
                    if (day.id != dayId) return@map day
                    val byId = day.activities.associateBy { it.id }
                    val reordered = orderedIds.mapNotNull { byId[it] }
                    day.copy(activities = reordered)
                }
            }
        }
    }

    override suspend fun trimOutOfRange(tripId: String, request: TrimOutOfRangeRequest) {
        api.trimOutOfRangeDays(tripId, request).requireSuccess()
        refreshItinerary(tripId)
    }

    private suspend fun findTripIdForDay(dayId: String): String? {
        val cache = itineraryCacheStore.getAll()
        return cache.entries.firstOrNull { entry ->
            entry.value.any { it.id == dayId }
        }?.key
    }

    private suspend fun findTripAndDayForActivity(activityId: String): Pair<String, String>? {
        val cache = itineraryCacheStore.getAll()
        cache.forEach { (tripId, days) ->
            days.forEach { day ->
                if (day.activities.any { it.id == activityId }) {
                    return tripId to day.id
                }
            }
        }
        return null
    }
}
