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
import nvk.cotrip.data.sync.SyncActivityReorderUpsertPayload
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncItineraryTrimUpsertPayload
import nvk.cotrip.data.sync.SyncQueueRepository
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException
import java.io.IOException
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
        }
        applyDayUpdateLocally(dayId = dayId, request = request)
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
            applyActivityUpdateLocally(activityId = activityId, request = request)
            throw OfflineWriteQueuedException(cause = e)
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
            applyActivityMoveLocally(activityId = activityId, request = request)
            throw OfflineWriteQueuedException(cause = e)
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
        var offlineQueued = false
        var offlineCause: IOException? = null
        try {
            api.deleteActivity(activityId).requireSuccess()
        } catch (e: IOException) {
            syncQueueRepository.enqueueDelete(SyncEntities.ACTIVITY, activityId)
            offlineQueued = true
            offlineCause = e
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
        if (offlineQueued) {
            throw OfflineWriteQueuedException(cause = offlineCause)
        }
    }

    override suspend fun reorderActivities(dayId: String, orderedIds: List<String>) {
        try {
            api.reorderActivities(dayId, ReorderActivitiesRequest(orderedIds)).requireSuccess()
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(
                entity = SyncEntities.ACTIVITY_REORDER,
                id = dayId,
                payload = SyncActivityReorderUpsertPayload(
                    dayId = dayId,
                    orderedIds = orderedIds,
                ),
            )
        }
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
        try {
            api.trimOutOfRangeDays(tripId, request).requireSuccess()
            refreshItinerary(tripId).getOrThrow()
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(
                entity = SyncEntities.ITINERARY_TRIM,
                id = tripId,
                payload = SyncItineraryTrimUpsertPayload(
                    tripId = tripId,
                    action = request.action,
                    dayIds = request.dayIds,
                ),
            )
            applyTrimOutOfRangeLocally(tripId = tripId, request = request)
        }
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

    private suspend fun applyDayUpdateLocally(
        dayId: String,
        request: UpdateDayRequest,
    ) {
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

    private suspend fun applyActivityUpdateLocally(
        activityId: String,
        request: UpdateActivityRequest,
    ) {
        val lookup = findTripAndDayForActivity(activityId) ?: return
        safeLocalMutation("updateActivity.offlineUpdate(activityId=$activityId)") {
            itineraryCacheStore.updateItinerary(lookup.first) { days ->
                days.map { day ->
                    if (day.id != lookup.second) {
                        day
                    } else {
                        day.copy(
                            activities = day.activities.map { activity ->
                                if (activity.id == activityId) {
                                    activity.copy(
                                        title = request.title ?: activity.title,
                                        timeText = request.timeText ?: activity.timeText,
                                        locationName = request.locationName ?: activity.locationName,
                                        link = request.link ?: activity.link,
                                        costAmount = request.costAmount ?: activity.costAmount,
                                        costType = request.costType ?: activity.costType,
                                        notes = request.notes ?: activity.notes,
                                    )
                                } else {
                                    activity
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private suspend fun applyActivityMoveLocally(
        activityId: String,
        request: MoveActivityRequest,
    ) {
        val source = findTripAndDayForActivity(activityId) ?: return
        safeLocalMutation("moveActivity.offlineMove(activityId=$activityId)") {
            itineraryCacheStore.updateItinerary(source.first) { days ->
                var movedActivity: ActivityDto? = null
                val without = days.map { day ->
                    if (day.id == source.second) {
                        val remaining = day.activities.filterNot { activity ->
                            val shouldRemove = activity.id == activityId
                            if (shouldRemove) {
                                movedActivity = activity
                            }
                            shouldRemove
                        }
                        day.copy(activities = remaining)
                    } else {
                        day
                    }
                }
                val candidate = movedActivity ?: return@updateItinerary without
                without.map { day ->
                    if (day.id != request.dayId) {
                        day
                    } else {
                        val targetOrder = request.orderIndex
                            ?: ((day.activities.maxOfOrNull { it.orderIndex } ?: -1) + 1)
                        val moved = candidate.copy(dayId = request.dayId, orderIndex = targetOrder)
                        day.copy(activities = (day.activities + moved).sortedBy { it.orderIndex })
                    }
                }
            }
        }
    }

    private suspend fun applyTrimOutOfRangeLocally(
        tripId: String,
        request: TrimOutOfRangeRequest,
    ) {
        safeLocalMutation("trimOutOfRange.offlineApply(action=${request.action},tripId=$tripId)") {
            itineraryCacheStore.updateItinerary(tripId) { days ->
                when (request.action) {
                    "keep" -> {
                        days.map { day ->
                            if (day.id in request.dayIds) {
                                day.copy(isOutOfRange = true)
                            } else {
                                day
                            }
                        }
                    }

                    "remove" -> {
                        days.filterNot { it.id in request.dayIds }
                    }

                    "extend_end" -> {
                        days.map { day ->
                            if (day.id in request.dayIds) {
                                day.copy(isOutOfRange = false)
                            } else {
                                day
                            }
                        }
                    }

                    else -> days
                }
            }
        }
    }
}
