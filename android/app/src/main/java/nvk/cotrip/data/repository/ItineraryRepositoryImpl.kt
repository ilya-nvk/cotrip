package nvk.cotrip.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import nvk.cotrip.data.cache.ItineraryCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CitySuggestionDto
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MoveActivityRequest
import nvk.cotrip.data.network.dto.PlaceSuggestionDto
import nvk.cotrip.data.network.dto.ReorderActivitiesRequest
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.UpdateActivityRequest
import nvk.cotrip.data.network.dto.UpdateDayRequest
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.data.sync.SyncActivityCreatePayload
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

class ItineraryRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
    private val itineraryCacheStore: ItineraryCacheStore,
    private val networkStateProvider: NetworkStateProvider,
) : ItineraryRepository {
    private companion object {
        private const val TAG = "ItineraryRepository"
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeItinerary(tripId: String): Flow<List<ItineraryDayDto>> {
        return itineraryCacheStore.observeItinerary(tripId)
    }

    override fun getItinerary(tripId: String): Flow<List<ItineraryDayDto>> {
        if (networkStateProvider.isOnline()) {
            ioScope.launch {
                runCatching {
                    val itinerary = fetchAllItineraryPages(tripId)
                    safeLocalMutation("getItinerary.setItinerary(tripId=$tripId)") {
                        itineraryCacheStore.setItinerary(tripId, itinerary)
                    }
                }.onFailure { error ->
                    AppLogger.w(TAG, "getItinerary network fetch failed for tripId=$tripId", error)
                }
            }
        }
        return itineraryCacheStore.observeItinerary(tripId)
    }

    override suspend fun refreshItinerary(tripId: String): Result<Unit> {
        return runCatching {
            val itinerary = fetchAllItineraryPages(tripId)
            safeLocalMutation("refreshItinerary.setItinerary(tripId=$tripId)") {
                itineraryCacheStore.setItinerary(tripId, itinerary)
            }
        }
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
        val activity = try {
            api.createActivity(dayId, request)
        } catch (e: IOException) {
            val orderIndex =
                resolveLocalOrderIndex(dayId = dayId, requestedOrderIndex = request.orderIndex)
            val localActivity = ActivityDto(
                id = UUID.randomUUID().toString(),
                dayId = dayId,
                sourceIdeaId = null,
                title = request.title,
                timeText = request.timeText,
                locationName = request.locationName,
                link = request.link,
                costAmount = request.costAmount,
                costType = request.costType,
                notes = request.notes,
                orderIndex = orderIndex,
            )
            syncQueueRepository.enqueueCreate(
                entity = SyncEntities.ACTIVITY,
                id = localActivity.id,
                payload = SyncActivityCreatePayload(
                    dayId = dayId,
                    title = request.title,
                    timeText = request.timeText,
                    locationName = request.locationName,
                    link = request.link,
                    costAmount = request.costAmount,
                    costType = request.costType,
                    notes = request.notes,
                    orderIndex = request.orderIndex,
                )
            )
            localActivity
        }

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
        refreshItinerary(tripId).getOrThrow()
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

    private suspend fun fetchAllItineraryPages(tripId: String): List<ItineraryDayDto> {
        val all = mutableListOf<ItineraryDayDto>()
        var cursor: String? = null
        do {
            val page = api.getItinerary(tripId = tripId, limit = 100, cursor = cursor)
            all += page.items
            cursor = page.nextCursor
        } while (cursor != null)
        return all
    }

    private suspend fun resolveLocalOrderIndex(dayId: String, requestedOrderIndex: Int?): Int {
        if (requestedOrderIndex != null) return requestedOrderIndex
        val tripId = findTripIdForDay(dayId) ?: return 0
        val itinerary = itineraryCacheStore.getItinerary(tripId)
        val day = itinerary.firstOrNull { it.id == dayId } ?: return 0
        val maxOrder = day.activities.maxOfOrNull { it.orderIndex } ?: -1
        return maxOrder + 1
    }
}
