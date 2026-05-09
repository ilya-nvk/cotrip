package nvk.cotrip.ui.trip.details

import androidx.navigation.NavOptionsBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CitySuggestionDto
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.dto.ExpenseCreateRequest
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.ExpenseUpdateRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.MoveActivityRequest
import nvk.cotrip.data.network.dto.PlaceSuggestionDto
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UpdateActivityRequest
import nvk.cotrip.data.network.dto.UpdateDayRequest
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import nvk.cotrip.data.network.dto.UpdateTripRequest
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.network.dto.UserDto
import nvk.cotrip.data.network.dto.WeatherForecastDto
import nvk.cotrip.data.network.dto.WeatherForecastResponseDto
import nvk.cotrip.data.repository.ExpenseRepository
import nvk.cotrip.data.repository.IdeaRepository
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.data.repository.WeatherRepository
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.time.LocalDate

internal data class TripDetailsWeatherRequest(
    val tripId: String,
    val city: String,
    val start: String?,
    val end: String?,
)

internal class TripDetailsFakeNavigator : AppNavigator {
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

internal class TripDetailsFakeTripRepository(
    trip: TripDto,
    members: List<MemberDto> = emptyList(),
) : TripRepository {
    private val tripsById = mutableMapOf(trip.id to trip)
    private val membersFlow = MutableStateFlow(members)

    override val trips: MutableStateFlow<List<TripDto>> = MutableStateFlow(listOf(trip))

    var refreshTripsResult: Result<Unit> = Result.success(Unit)
    var getTripError: Throwable? = null

    override fun getTrip(tripId: String): Flow<TripDto> = flow {
        getTripError?.let { throw it }
        emit(tripsById[tripId] ?: error("Unknown trip: $tripId"))
    }

    override suspend fun refreshTrips(): Result<Unit> = refreshTripsResult

    override suspend fun createTrip(request: CreateTripRequest): String = "trip-created"

    override suspend fun updateTrip(tripId: String, request: UpdateTripRequest): Result<Unit> =
        Result.success(Unit)

    override suspend fun archiveTrip(tripId: String) = Unit

    override suspend fun deleteTrip(tripId: String) = Unit

    override fun tripMembers(tripId: String): Flow<List<MemberDto>> = membersFlow

    var removeMemberError: Throwable? = null
    override suspend fun removeMember(tripId: String, memberId: String) {
        removeMemberError?.let { throw it }
    }

    fun setTrip(trip: TripDto) {
        tripsById[trip.id] = trip
        trips.value = trips.value.filterNot { it.id == trip.id } + trip
    }

    fun setMembers(members: List<MemberDto>) {
        membersFlow.value = members
    }
}

internal class TripDetailsFakeUserRepository(
    me: UserDto?,
) : UserRepository {
    override val me: MutableStateFlow<UserDto?> = MutableStateFlow(me)

    override suspend fun refreshMe(): Result<Unit> = Result.success(Unit)

    override suspend fun updateMe(request: UpdateUserRequest): UserDto {
        return me.value ?: tripDetailsUserDto(id = "user-unknown")
    }

    override suspend fun deleteMe() = Unit

    override fun clearSession() = Unit
}

internal class TripDetailsFakeIdeaRepository(
    ideas: List<IdeaDto> = emptyList(),
) : IdeaRepository {
    private val ideasFlow = MutableStateFlow(ideas)

    var refreshIdeasResult: Result<Unit> = Result.success(Unit)

    override fun observeIdeas(tripId: String): Flow<List<IdeaDto>> = ideasFlow

    override fun getIdea(ideaId: String): Flow<IdeaDto> = flowOf(
        ideasFlow.value.firstOrNull { it.id == ideaId } ?: tripDetailsIdeaDto(id = ideaId)
    )

    override fun observeComments(ideaId: String): Flow<List<CommentDto>> = flowOf(emptyList())

    override suspend fun createIdea(tripId: String, request: CreateIdeaRequest): IdeaDto =
        tripDetailsIdeaDto(id = "idea-created", tripId = tripId, title = request.title)

    override suspend fun updateIdea(ideaId: String, request: UpdateIdeaRequest) = Unit

    override suspend fun deleteIdea(ideaId: String) = Unit

    override suspend fun deleteComment(commentId: String) = Unit

    override suspend fun convertIdeaToActivity(ideaId: String, request: ConvertIdeaRequest) = Unit

    override suspend fun approveIdea(ideaId: String): IdeaDto = tripDetailsIdeaDto(id = ideaId)

    override suspend fun rejectIdea(ideaId: String): IdeaDto = tripDetailsIdeaDto(id = ideaId)

    override suspend fun refreshIdeas(tripId: String): Result<Unit> = refreshIdeasResult

    override suspend fun refreshComments(ideaId: String): Result<Unit> = Result.success(Unit)

    fun setIdeas(ideas: List<IdeaDto>) {
        ideasFlow.value = ideas
    }
}

internal class TripDetailsFakeExpenseRepository(
    expenses: List<ExpenseDto> = emptyList(),
) : ExpenseRepository {
    private val expensesFlow = MutableStateFlow(expenses)

    var refreshExpensesResult: Result<Unit> = Result.success(Unit)

    override fun observeExpenses(tripId: String): Flow<List<ExpenseDto>> = expensesFlow

    override fun getExpense(expenseId: String): Flow<ExpenseDto> = flowOf(
        expensesFlow.value.firstOrNull { it.id == expenseId }
            ?: tripDetailsExpenseDto(id = expenseId, amount = 0.0)
    )

    override suspend fun createExpense(tripId: String, request: ExpenseCreateRequest): ExpenseDto =
        tripDetailsExpenseDto(
            id = "expense-created",
            tripId = tripId,
            amount = request.amount,
            title = request.title,
        )

    override suspend fun updateExpense(expenseId: String, request: ExpenseUpdateRequest) = Unit

    override suspend fun deleteExpense(expenseId: String) = Unit

    override suspend fun refreshExpenses(tripId: String): Result<Unit> = refreshExpensesResult

    fun setExpenses(expenses: List<ExpenseDto>) {
        expensesFlow.value = expenses
    }
}

internal class TripDetailsFakeItineraryRepository(
    days: List<ItineraryDayDto> = emptyList(),
) : ItineraryRepository {
    private val itineraryFlow = MutableStateFlow(days)

    var refreshItineraryResult: Result<Unit> = Result.success(Unit)

    override fun observeItinerary(tripId: String): Flow<List<ItineraryDayDto>> = itineraryFlow

    override fun getItinerary(tripId: String): Flow<List<ItineraryDayDto>> = itineraryFlow

    override suspend fun refreshItinerary(tripId: String): Result<Unit> = refreshItineraryResult

    override suspend fun searchCities(
        tripId: String,
        query: String,
        limit: Int,
    ): List<CitySuggestionDto> = emptyList()

    override suspend fun searchPlaces(
        tripId: String,
        query: String,
        limit: Int,
    ): List<PlaceSuggestionDto> = emptyList()

    override suspend fun updateDay(dayId: String, request: UpdateDayRequest) = Unit

    override suspend fun updateDaysCity(tripId: String, dayIds: List<String>, request: UpdateDayRequest) = Unit

    override suspend fun createActivity(dayId: String, request: CreateActivityRequest): ActivityDto =
        tripDetailsActivityDto(id = "activity-created", dayId = dayId, title = request.title)

    override suspend fun updateActivity(activityId: String, request: UpdateActivityRequest) = Unit

    override suspend fun moveActivity(activityId: String, request: MoveActivityRequest) = Unit

    override suspend fun deleteActivity(activityId: String) = Unit

    override suspend fun reorderActivities(dayId: String, orderedIds: List<String>) = Unit

    override suspend fun trimOutOfRange(tripId: String, request: TrimOutOfRangeRequest) = Unit

    fun setDays(days: List<ItineraryDayDto>) {
        itineraryFlow.value = days
    }
}

internal class TripDetailsFakeWeatherRepository : WeatherRepository {
    private val cachedResponses = mutableMapOf<String, WeatherForecastResponseDto>()
    private val flowResponses = mutableMapOf<String, WeatherForecastResponseDto>()
    private val snapshots = mutableMapOf<String, Result<WeatherForecastResponseDto>>()

    val cachedRequests = mutableListOf<TripDetailsWeatherRequest>()
    val refreshRequests = mutableListOf<TripDetailsWeatherRequest>()
    val snapshotRequests = mutableListOf<TripDetailsWeatherRequest>()
    val weatherRequests = mutableListOf<TripDetailsWeatherRequest>()

    var refreshError: Throwable? = null

    override suspend fun getCachedWeather(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): WeatherForecastResponseDto? {
        cachedRequests += TripDetailsWeatherRequest(tripId, city, start, end)
        return cachedResponses[city]
    }

    override fun getWeather(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): Flow<WeatherForecastResponseDto> {
        weatherRequests += TripDetailsWeatherRequest(tripId, city, start, end)
        return flowOf(flowResponses[city] ?: WeatherForecastResponseDto())
    }

    override suspend fun refreshWeather(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): Result<Unit> {
        refreshRequests += TripDetailsWeatherRequest(tripId, city, start, end)
        return refreshError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun fetchWeatherSnapshot(
        tripId: String,
        city: String,
        start: String?,
        end: String?,
    ): Result<WeatherForecastResponseDto> {
        snapshotRequests += TripDetailsWeatherRequest(tripId, city, start, end)
        return snapshots[city] ?: Result.failure(IllegalStateException("No snapshot for city=$city"))
    }

    fun setCachedResponse(city: String, response: WeatherForecastResponseDto) {
        cachedResponses[city] = response
    }

    fun setFlowResponse(city: String, response: WeatherForecastResponseDto) {
        flowResponses[city] = response
    }

    fun setSnapshot(city: String, snapshot: Result<WeatherForecastResponseDto>) {
        snapshots[city] = snapshot
    }
}

internal fun tripDetailsTripDto(
    id: String = "trip-1",
    ownerId: String = "owner-1",
    title: String = "Spring Rome",
    start: LocalDate,
    end: LocalDate,
    currencyCode: String = "EUR",
    locationLine: String? = "Rome, Italy",
): TripDto = TripDto(
    id = id,
    ownerId = ownerId,
    title = title,
    description = null,
    startDate = start.toString(),
    endDate = end.toString(),
    locationLine = locationLine,
    coverUrl = null,
    currencyCode = currencyCode,
    status = "active",
    updatedAt = "2026-03-16T10:00:00Z",
)

internal fun tripDetailsMemberDto(
    id: String,
    initials: String,
    name: String = initials,
): MemberDto = MemberDto(
    userId = id,
    name = name,
    photoUrl = null,
    initials = initials,
    role = "member",
    status = "accepted",
)

internal fun tripDetailsIdeaDto(
    id: String,
    tripId: String = "trip-1",
    title: String = "Idea $id",
): IdeaDto = IdeaDto(
    id = id,
    tripId = tripId,
    authorId = "author-1",
    title = title,
    city = "Rome",
    link = null,
    costAmount = null,
    costType = null,
    notes = null,
    status = "suggested",
    updatedAt = "2026-03-16T10:00:00Z",
    commentsCount = 0,
)

internal fun tripDetailsExpenseDto(
    id: String,
    tripId: String = "trip-1",
    title: String = "Expense $id",
    amount: Double,
): ExpenseDto = ExpenseDto(
    id = id,
    tripId = tripId,
    title = title,
    amount = amount,
    currencyCode = "EUR",
    status = "planned",
    paidById = null,
    date = null,
    splitType = "equal",
    note = null,
    participants = emptyList(),
)

internal fun tripDetailsDayDto(
    id: String,
    dayNumber: Int,
    date: LocalDate,
    city: String?,
    isOutOfRange: Boolean = false,
    activities: List<ActivityDto> = emptyList(),
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

internal fun tripDetailsActivityDto(
    id: String,
    dayId: String,
    title: String,
    order: Int = 0,
    timeText: String? = null,
): ActivityDto = ActivityDto(
    id = id,
    dayId = dayId,
    sourceIdeaId = null,
    title = title,
    timeText = timeText,
    locationName = null,
    link = null,
    costAmount = null,
    costType = null,
    notes = null,
    orderIndex = order,
)

internal fun tripDetailsUserDto(
    id: String,
    name: String = "User $id",
): UserDto = UserDto(
    id = id,
    name = name,
    photoUrl = null,
    initials = name.take(2).uppercase(),
)

internal fun tripDetailsWeatherResponse(
    city: String,
    date: LocalDate,
    description: String = "clear sky",
): WeatherForecastResponseDto = WeatherForecastResponseDto(
    items = listOf(
        WeatherForecastDto(
            id = "weather-$city",
            tripId = "trip-1",
            city = city,
            date = date.toString(),
            tempMin = 12.2,
            tempMax = 20.4,
            description = description,
            iconCode = "01d",
            source = "OpenWeather",
            fetchedAt = "2026-03-16T09:30:00Z",
        )
    ),
    missingDates = emptyList(),
)
