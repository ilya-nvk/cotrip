package nvk.cotrip.ui.forecast

import androidx.navigation.NavOptionsBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import nvk.cotrip.data.network.dto.WeatherForecastDto
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.WeatherRepository
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination

internal data class WeatherRequest(
    val tripId: String,
    val city: String,
    val start: String?,
    val end: String?,
)

internal class FakeNavigator : AppNavigator {
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

internal class FakeTripRepository(
    private val tripById: MutableMap<String, TripDto> = mutableMapOf(),
    override val trips: MutableStateFlow<List<TripDto>> = MutableStateFlow(emptyList()),
) : TripRepository {
    override fun getTrip(tripId: String): Flow<TripDto> {
        return flowOf(tripById[tripId] ?: tripDto(id = tripId))
    }

    override suspend fun refreshTrips(): Result<Unit> = Result.success(Unit)

    override suspend fun createTrip(request: CreateTripRequest): String = "trip-created"

    override suspend fun updateTrip(tripId: String, request: UpdateTripRequest): Result<Unit> =
        Result.success(Unit)

    override suspend fun archiveTrip(tripId: String) = Unit

    override suspend fun deleteTrip(tripId: String) = Unit

    override fun tripMembers(tripId: String): Flow<List<MemberDto>> = flowOf(emptyList())

    override suspend fun removeMember(tripId: String, memberId: String) = Unit

    fun setTrip(trip: TripDto) {
        tripById[trip.id] = trip
    }
}

internal class FakeItineraryRepository(
    initial: List<ItineraryDayDto> = emptyList(),
) : ItineraryRepository {
    private val itineraryFlow = MutableStateFlow(initial)
    val refreshCalls = mutableListOf<String>()
    var refreshResult: Result<Unit> = Result.success(Unit)

    override fun observeItinerary(tripId: String): Flow<List<ItineraryDayDto>> = itineraryFlow

    override fun getItinerary(tripId: String): Flow<List<ItineraryDayDto>> = itineraryFlow

    override suspend fun refreshItinerary(tripId: String): Result<Unit> {
        refreshCalls += tripId
        return refreshResult
    }

    override suspend fun searchCities(tripId: String, query: String, limit: Int): List<CitySuggestionDto> =
        emptyList()

    override suspend fun searchPlaces(tripId: String, query: String, limit: Int): List<PlaceSuggestionDto> =
        emptyList()

    override suspend fun updateDay(dayId: String, request: UpdateDayRequest) = Unit

    override suspend fun createActivity(dayId: String, request: CreateActivityRequest): ActivityDto {
        return ActivityDto(
            id = "activity-1",
            dayId = dayId,
            title = request.title,
            timeText = request.timeText,
            locationName = request.locationName,
            link = request.link,
            costAmount = request.costAmount,
            costType = request.costType,
            notes = request.notes,
            orderIndex = request.orderIndex ?: 0,
        )
    }

    override suspend fun updateActivity(activityId: String, request: UpdateActivityRequest) = Unit

    override suspend fun moveActivity(activityId: String, request: MoveActivityRequest) = Unit

    override suspend fun deleteActivity(activityId: String) = Unit

    override suspend fun reorderActivities(dayId: String, orderedIds: List<String>) = Unit

    override suspend fun trimOutOfRange(tripId: String, request: TrimOutOfRangeRequest) = Unit

    fun setItinerary(days: List<ItineraryDayDto>) {
        itineraryFlow.value = days
    }
}

internal class FakeWeatherRepository : WeatherRepository {
    val refreshRequests = mutableListOf<WeatherRequest>()
    val getWeatherRequests = mutableListOf<WeatherRequest>()

    var refreshError: Throwable? = null
    var defaultResponse: WeatherForecastResponseDto = WeatherForecastResponseDto()
    private val responseByCity = mutableMapOf<String, WeatherForecastResponseDto>()

    override suspend fun getCachedWeather(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): WeatherForecastResponseDto? {
        return responseByCity[city] ?: defaultResponse
    }

    override fun getWeather(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): Flow<WeatherForecastResponseDto> {
        getWeatherRequests += WeatherRequest(tripId = tripId, city = city, start = start, end = end)
        return flowOf(responseByCity[city] ?: defaultResponse)
    }

    override suspend fun refreshWeather(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): Result<Unit> {
        refreshRequests += WeatherRequest(tripId = tripId, city = city, start = start, end = end)
        return refreshError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun fetchWeatherSnapshot(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): Result<WeatherForecastResponseDto> {
        return Result.success(responseByCity[city] ?: defaultResponse)
    }

    fun setResponse(city: String, response: WeatherForecastResponseDto) {
        responseByCity[city] = response
    }
}

internal fun tripDto(
    id: String,
    startDate: String = "2026-06-10",
    endDate: String = "2026-06-14",
): TripDto = TripDto(
    id = id,
    ownerId = "owner",
    title = "Trip $id",
    description = null,
    startDate = startDate,
    endDate = endDate,
    locationLine = null,
    coverUrl = null,
    currencyCode = "EUR",
    status = "active",
    updatedAt = "2026-03-16T10:00:00Z",
)

internal fun dayDto(
    id: String,
    dayNumber: Int,
    city: String?,
    lat: Double? = null,
    lon: Double? = null,
): ItineraryDayDto = ItineraryDayDto(
    id = id,
    tripId = "trip-1",
    date = "2026-06-${10 + dayNumber}",
    dayNumber = dayNumber,
    city = city,
    cityDisplayName = null,
    cityProviderId = null,
    cityLat = lat,
    cityLon = lon,
    isOutOfRange = false,
    activities = emptyList(),
)

internal fun weatherResponse(
    source: String = "OpenWeather",
    fetchedAt: String = "2026-03-16T09:30:00Z",
    missingDates: List<String> = emptyList(),
): WeatherForecastResponseDto = WeatherForecastResponseDto(
    items = listOf(
        WeatherForecastDto(
            id = "wf-1",
            tripId = "trip-1",
            city = "Rome",
            date = "2026-06-10",
            tempMin = 12.2,
            tempMax = 20.4,
            description = "clear sky",
            iconCode = "01d",
            source = source,
            fetchedAt = fetchedAt,
        )
    ),
    missingDates = missingDates,
)
