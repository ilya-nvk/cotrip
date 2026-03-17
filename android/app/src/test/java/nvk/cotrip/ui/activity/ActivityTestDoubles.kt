package nvk.cotrip.ui.activity

import androidx.navigation.NavOptionsBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CitySuggestionDto
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MoveActivityRequest
import nvk.cotrip.data.network.dto.PlaceSuggestionDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.UpdateActivityRequest
import nvk.cotrip.data.network.dto.UpdateDayRequest
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.time.LocalDate

internal class ActivityFakeNavigator : AppNavigator {
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

internal class ActivityFakeTripRepository(
    trip: TripDto,
    tripsList: List<TripDto>? = null,
) : TripRepository {
    private val tripsFlow = MutableStateFlow(tripsList ?: listOf(trip))

    var refreshTripsResult: Result<Unit> = Result.success(Unit)
    var getTripError: Throwable? = null

    override val trips: Flow<List<TripDto>> = tripsFlow

    override fun getTrip(tripId: String): Flow<TripDto> = kotlinx.coroutines.flow.flow {
        getTripError?.let { throw it }
        emit(requireNotNull(tripsFlow.value.firstOrNull { it.id == tripId }) {
            "No trip found for $tripId"
        })
    }

    override suspend fun refreshTrips(): Result<Unit> = refreshTripsResult

    override suspend fun createTrip(request: nvk.cotrip.data.network.dto.CreateTripRequest): String = "trip-1"
    override suspend fun updateTrip(tripId: String, request: nvk.cotrip.data.network.dto.UpdateTripRequest): Result<Unit> =
        Result.success(Unit)
    override suspend fun archiveTrip(tripId: String) = Unit
    override suspend fun deleteTrip(tripId: String) = Unit
    override fun tripMembers(tripId: String): Flow<List<nvk.cotrip.data.network.dto.MemberDto>> = flowOf(emptyList())
    override suspend fun removeMember(tripId: String, memberId: String) = Unit

    fun setTrips(trips: List<TripDto>) {
        tripsFlow.value = trips
    }
}

internal class ActivityFakeItineraryRepository(
    initialDays: List<ItineraryDayDto> = emptyList(),
    initialTripId: String = "trip-1",
    initialDaysByTripId: Map<String, List<ItineraryDayDto>>? = null,
) : ItineraryRepository {
    private val itineraryByTripId = mutableMapOf<String, MutableStateFlow<List<ItineraryDayDto>>>()

    init {
        if (initialDaysByTripId != null) {
            initialDaysByTripId.forEach { (id, days) ->
                itineraryByTripId[id] = MutableStateFlow(days)
            }
        } else if (initialDays.isNotEmpty()) {
            itineraryByTripId[initialTripId] = MutableStateFlow(initialDays)
        }
    }

    var createActivityResult: ActivityDto? = null
    var createActivityToThrow: Throwable? = null
    val createActivityCalls = mutableListOf<Pair<String, CreateActivityRequest>>()
    val updateActivityCalls = mutableListOf<Pair<String, UpdateActivityRequest>>()
    val deleteActivityCalls = mutableListOf<String>()

    override fun observeItinerary(tripId: String): Flow<List<ItineraryDayDto>> =
        itineraryByTripId.getOrPut(tripId) { MutableStateFlow(emptyList()) }

    override fun getItinerary(tripId: String): Flow<List<ItineraryDayDto>> =
        observeItinerary(tripId)

    override suspend fun refreshItinerary(tripId: String): Result<Unit> = Result.success(Unit)

    override suspend fun searchCities(tripId: String, query: String, limit: Int): List<CitySuggestionDto> = emptyList()

    override suspend fun searchPlaces(tripId: String, query: String, limit: Int): List<PlaceSuggestionDto> = emptyList()

    override suspend fun updateDay(dayId: String, request: UpdateDayRequest) = Unit

    override suspend fun createActivity(dayId: String, request: CreateActivityRequest): ActivityDto {
        createActivityToThrow?.let { throw it }
        createActivityCalls += dayId to request
        return createActivityResult ?: activityDto(
            id = "activity-created",
            dayId = dayId,
            title = request.title,
        )
    }

    override suspend fun updateActivity(activityId: String, request: UpdateActivityRequest) {
        updateActivityCalls += activityId to request
    }

    override suspend fun moveActivity(activityId: String, request: MoveActivityRequest) = Unit

    override suspend fun deleteActivity(activityId: String) {
        deleteActivityCalls += activityId
    }

    override suspend fun reorderActivities(dayId: String, orderedIds: List<String>) = Unit

    override suspend fun trimOutOfRange(tripId: String, request: TrimOutOfRangeRequest) = Unit

    fun setItinerary(tripId: String, days: List<ItineraryDayDto>) {
        itineraryByTripId[tripId] = MutableStateFlow(days)
    }
}

internal fun activityTripDto(
    id: String = "trip-1",
    startDate: String = LocalDate.now().toString(),
    endDate: String = LocalDate.now().plusDays(5).toString(),
    currencyCode: String = "EUR",
): TripDto = TripDto(
    id = id,
    ownerId = "owner-1",
    title = "Test Trip",
    description = null,
    startDate = startDate,
    endDate = endDate,
    locationLine = null,
    coverUrl = null,
    currencyCode = currencyCode,
    status = "active",
    updatedAt = "2026-03-16T10:00:00Z",
)

internal fun activityDayDto(
    id: String = "day-1",
    tripId: String = "trip-1",
    date: String = LocalDate.now().toString(),
    dayNumber: Int = 1,
    city: String? = "Paris",
    isOutOfRange: Boolean = false,
    activities: List<ActivityDto> = emptyList(),
): ItineraryDayDto = ItineraryDayDto(
    id = id,
    tripId = tripId,
    date = date,
    dayNumber = dayNumber,
    city = city,
    cityProviderId = null,
    cityLat = null,
    cityLon = null,
    isOutOfRange = isOutOfRange,
    activities = activities,
)

internal fun activityDto(
    id: String = "activity-1",
    dayId: String = "day-1",
    title: String = "Test Activity",
    timeText: String? = "10:00",
    locationName: String? = null,
    link: String? = null,
    costAmount: Double? = null,
    costType: String? = "per_person",
    notes: String? = null,
    orderIndex: Int = 0,
): ActivityDto = ActivityDto(
    id = id,
    dayId = dayId,
    sourceIdeaId = null,
    title = title,
    timeText = timeText,
    locationName = locationName,
    link = link,
    costAmount = costAmount,
    costType = costType,
    notes = notes,
    orderIndex = orderIndex,
)
