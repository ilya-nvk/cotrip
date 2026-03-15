package nvk.cotrip.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import nvk.cotrip.data.cache.ExpensesCacheStore
import nvk.cotrip.data.cache.IdeasCacheStore
import nvk.cotrip.data.cache.ItineraryCacheStore
import nvk.cotrip.data.cache.TripsCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.SyncChangeDto
import nvk.cotrip.data.network.dto.TripDto
import java.time.OffsetDateTime
import javax.inject.Inject

class SyncPullRepository @Inject constructor(
    private val api: CoTripApi,
    private val json: Json,
    private val syncStateStore: SyncStateStore,
    private val tripsCacheStore: TripsCacheStore,
    private val ideasCacheStore: IdeasCacheStore,
    private val expensesCacheStore: ExpensesCacheStore,
    private val itineraryCacheStore: ItineraryCacheStore,
) {
    suspend fun pull(): Result<Unit> {
        return runCatching {
            val sinceRaw = syncStateStore.getLastSync()
            val since = sinceRaw?.let { OffsetDateTime.parse(it) }
                ?: OffsetDateTime.parse("1970-01-01T00:00:00Z")
            var cursor: String? = null
            var maxUpdated: OffsetDateTime? = null
            do {
                val response = api.getSyncChanges(
                    since = since.toString(),
                    limit = 100,
                    cursor = cursor,
                )
                applyChanges(response.items)
                val pageMax = response.items.mapNotNull { parseUpdatedAt(it) }.maxOrNull()
                if (pageMax != null && (maxUpdated == null || pageMax.isAfter(maxUpdated))) {
                    maxUpdated = pageMax
                }
                cursor = response.nextCursor
            } while (cursor != null)
            val nextCursor = maxUpdated ?: OffsetDateTime.now()
            syncStateStore.setLastSync(nextCursor.toString())
        }
    }

    private suspend fun applyChanges(items: List<SyncChangeDto>) {
        items.forEach { change ->
            when (change.entity) {
                "trip" -> applyTrip(change)
                "idea" -> applyIdea(change)
                "expense" -> applyExpense(change)
                "day",
                "itinerary_day" -> applyItineraryDay(change)
                "activity" -> applyActivity(change)
            }
        }
    }

    private suspend fun applyTrip(change: SyncChangeDto) {
        val trip = decodeOrNull<TripDto>(change) ?: return
        if (change.deletedAt != null) {
            tripsCacheStore.removeTrip(trip.id)
            ideasCacheStore.clearTrip(trip.id)
            expensesCacheStore.clearTrip(trip.id)
            itineraryCacheStore.clearTrip(trip.id)
        } else {
            tripsCacheStore.upsertTrip(trip)
        }
    }

    private suspend fun applyIdea(change: SyncChangeDto) {
        val idea = decodeOrNull<IdeaDto>(change) ?: return
        if (change.deletedAt != null) {
            ideasCacheStore.removeIdea(idea.tripId, idea.id)
        } else {
            ideasCacheStore.upsertIdea(idea.tripId, idea)
        }
    }

    private suspend fun applyExpense(change: SyncChangeDto) {
        val expense = decodeOrNull<ExpenseDto>(change) ?: return
        if (change.deletedAt != null) {
            expensesCacheStore.removeExpense(expense.tripId, expense.id)
        } else {
            expensesCacheStore.upsertExpense(expense.tripId, expense)
        }
    }

    private suspend fun applyItineraryDay(change: SyncChangeDto) {
        val day = decodeOrNull<ItineraryDayDto>(change) ?: return
        if (change.deletedAt != null) {
            itineraryCacheStore.updateItinerary(day.tripId) { days ->
                days.filterNot { it.id == day.id }
            }
            return
        }

        itineraryCacheStore.updateItinerary(day.tripId) { days ->
            val existingIndex = days.indexOfFirst { it.id == day.id }
            val merged = if (existingIndex >= 0) {
                day.copy(activities = days[existingIndex].activities)
            } else {
                day
            }
            val updated = days.toMutableList()
            if (existingIndex >= 0) {
                updated[existingIndex] = merged
            } else {
                updated.add(merged)
            }
            updated.sortedBy { it.dayNumber }
        }
    }

    private suspend fun applyActivity(change: SyncChangeDto) {
        val activity = decodeOrNull<ActivityDto>(change) ?: return
        val tripId = findTripIdForDay(activity.dayId) ?: return
        if (change.deletedAt != null) {
            itineraryCacheStore.updateItinerary(tripId) { days ->
                days.map { day ->
                    if (day.id == activity.dayId) {
                        day.copy(activities = day.activities.filterNot { it.id == activity.id })
                    } else {
                        day
                    }
                }
            }
            return
        }

        itineraryCacheStore.updateItinerary(tripId) { days ->
            days.map { day ->
                if (day.id != activity.dayId) return@map day
                val existing = day.activities.filterNot { it.id == activity.id }
                val updated = (existing + activity).sortedBy { it.orderIndex }
                day.copy(activities = updated)
            }
        }
    }

    private suspend fun findTripIdForDay(dayId: String): String? {
        val cache = itineraryCacheStore.getAll()
        return cache.entries.firstOrNull { entry ->
            entry.value.any { it.id == dayId }
        }?.key
    }

    private inline fun <reified T> decodeOrNull(change: SyncChangeDto): T? {
        if (change.payload is JsonNull) return null
        return runCatching { json.decodeFromString<T>(change.payload.toString()) }.getOrNull()
    }

    private fun parseUpdatedAt(change: SyncChangeDto): OffsetDateTime? {
        return runCatching { OffsetDateTime.parse(change.updatedAt) }.getOrNull()
    }
}
