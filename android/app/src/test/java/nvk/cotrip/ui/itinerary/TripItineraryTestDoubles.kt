package nvk.cotrip.ui.itinerary

import androidx.navigation.NavOptionsBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CitySuggestionDto
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.MoveActivityRequest
import nvk.cotrip.data.network.dto.PlaceSuggestionDto
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UpdateActivityRequest
import nvk.cotrip.data.network.dto.UpdateDayRequest
import nvk.cotrip.data.network.dto.UpdateTripRequest
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.time.LocalDate

internal class TripItineraryFakeNavigator : AppNavigator {
    val destinations = mutableListOf<Destination>()
    var popCalls: Int = 0

    override fun navigate(
        destination: Destination,
        navOptions: (NavOptionsBuilder.() -> Unit)?,
    ) {
        destinations += destination
    }

    override fun popBackStack(): Boolean {
        popCalls += 1
        return true
    }
}

internal class TripItineraryFakeTripRepository(
    trip: TripDto,
) : TripRepository {
    private val tripById = mutableMapOf(trip.id to trip)
    val deletedTripIds = mutableListOf<String>()

    var getTripError: Throwable? = null
    override val trips: MutableStateFlow<List<TripDto>> = MutableStateFlow(listOf(trip))

    override fun getTrip(tripId: String): Flow<TripDto> = flow {
        getTripError?.let { throw it }
        emit(tripById[tripId] ?: error("Unknown trip $tripId"))
    }

    override suspend fun refreshTrips(): Result<Unit> = Result.success(Unit)

    override suspend fun createTrip(request: CreateTripRequest): String = "trip-created"

    override suspend fun updateTrip(tripId: String, request: UpdateTripRequest): Result<Unit> =
        Result.success(Unit)

    override suspend fun archiveTrip(tripId: String) = Unit

    override suspend fun deleteTrip(tripId: String) {
        deletedTripIds += tripId
    }

    override fun tripMembers(tripId: String): Flow<List<MemberDto>> = flowOf(emptyList())

    override suspend fun removeMember(tripId: String, memberId: String) = Unit
}

internal class TripItineraryFakeRepository(
    days: List<ItineraryDayDto> = emptyList(),
) : ItineraryRepository {
    private val daysFlow = MutableStateFlow(days)

    val refreshCalls = mutableListOf<String>()
    val updateCalls = mutableListOf<Pair<String, UpdateDayRequest>>()
    val searchCalls = mutableListOf<String>()

    var refreshResult: Result<Unit> = Result.success(Unit)
    var searchResults: List<CitySuggestionDto> = emptyList()
    var searchError: Throwable? = null

    override fun observeItinerary(tripId: String): Flow<List<ItineraryDayDto>> = daysFlow

    override fun getItinerary(tripId: String): Flow<List<ItineraryDayDto>> = daysFlow

    override suspend fun refreshItinerary(tripId: String): Result<Unit> {
        refreshCalls += tripId
        return refreshResult
    }

    override suspend fun searchCities(
        tripId: String,
        query: String,
        limit: Int,
    ): List<CitySuggestionDto> {
        searchCalls += query
        searchError?.let { throw it }
        return searchResults
    }

    override suspend fun searchPlaces(
        tripId: String,
        query: String,
        limit: Int,
    ): List<PlaceSuggestionDto> = emptyList()

    override suspend fun updateDay(dayId: String, request: UpdateDayRequest) {
        updateCalls += dayId to request
        daysFlow.value = daysFlow.value.map { day ->
            if (day.id == dayId) {
                day.copy(
                    city = request.city,
                    cityProviderId = request.cityProviderId,
                    cityLat = request.cityLat,
                    cityLon = request.cityLon,
                    cityDisplayName = null,
                )
            } else {
                day
            }
        }
    }

    override suspend fun updateDaysCity(tripId: String, dayIds: List<String>, request: UpdateDayRequest) {
        dayIds.forEach { dayId -> updateCalls += dayId to request }
        val idSet = dayIds.toSet()
        daysFlow.value = daysFlow.value.map { day ->
            if (day.id in idSet) {
                day.copy(
                    city = request.city,
                    cityProviderId = request.cityProviderId,
                    cityLat = request.cityLat,
                    cityLon = request.cityLon,
                    cityDisplayName = null,
                )
            } else {
                day
            }
        }
    }

    override suspend fun createActivity(dayId: String, request: CreateActivityRequest): ActivityDto =
        itineraryActivityDto(
            id = "activity-created",
            dayId = dayId,
            title = request.title,
            order = request.orderIndex ?: 0,
        )

    override suspend fun updateActivity(activityId: String, request: UpdateActivityRequest) = Unit

    override suspend fun moveActivity(activityId: String, request: MoveActivityRequest) = Unit

    override suspend fun deleteActivity(activityId: String) = Unit

    override suspend fun reorderActivities(dayId: String, orderedIds: List<String>) = Unit

    override suspend fun trimOutOfRange(tripId: String, request: TrimOutOfRangeRequest) = Unit

    fun setDays(days: List<ItineraryDayDto>) {
        daysFlow.value = days
    }
}

internal fun itineraryTripDto(
    id: String = "trip-1",
    start: LocalDate,
    end: LocalDate,
    currencyCode: String = "EUR",
): TripDto = TripDto(
    id = id,
    ownerId = "owner-1",
    title = "Trip $id",
    description = null,
    startDate = start.toString(),
    endDate = end.toString(),
    locationLine = "Rome",
    coverUrl = null,
    currencyCode = currencyCode,
    status = "active",
    updatedAt = "2026-03-16T10:00:00Z",
)

internal fun itineraryDayDto(
    id: String,
    dayNumber: Int,
    date: LocalDate,
    city: String?,
    activities: List<ActivityDto> = emptyList(),
    isOutOfRange: Boolean = false,
    lat: Double? = if (city == null) null else 41.9,
    lon: Double? = if (city == null) null else 12.5,
): ItineraryDayDto = ItineraryDayDto(
    id = id,
    tripId = "trip-1",
    date = date.toString(),
    dayNumber = dayNumber,
    city = city,
    cityProviderId = null,
    cityLat = lat,
    cityLon = lon,
    isOutOfRange = isOutOfRange,
    activities = activities,
)

internal fun itineraryActivityDto(
    id: String,
    dayId: String,
    title: String,
    order: Int,
    time: String = "09:00",
    location: String? = null,
    cost: Double? = null,
): ActivityDto = ActivityDto(
    id = id,
    dayId = dayId,
    sourceIdeaId = null,
    title = title,
    timeText = time,
    locationName = location,
    link = null,
    costAmount = cost,
    costType = null,
    notes = null,
    orderIndex = order,
)
