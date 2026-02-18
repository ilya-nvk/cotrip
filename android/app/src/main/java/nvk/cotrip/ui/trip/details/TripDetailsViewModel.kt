package nvk.cotrip.ui.trip.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.repository.ExpenseRepository
import nvk.cotrip.data.repository.IdeaRepository
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.data.repository.WeatherRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.components.AvatarStackItem
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.Info
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.Warning
import nvk.cotrip.util.AppLogger
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class TripDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val userRepository: UserRepository,
    private val ideaRepository: IdeaRepository,
    private val expenseRepository: ExpenseRepository,
    private val itineraryRepository: ItineraryRepository,
    private val weatherRepository: WeatherRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])
    private var latestWeather: WeatherCardUi = TripDetailsWeatherMapper.cityMissingCard()

    private val _state =
        MutableStateFlow<TripDetailsState>(TripDetailsState.Loading)
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripDetailsEffect>()
    val effects = _effects.asSharedFlow()

    init {
        observeCachedTripData()
        primeTripCache()
        refreshTripData(isUserRefresh = false)
    }

    fun onEvent(event: TripDetailsEvent) {
        when (event) {
            TripDetailsEvent.OnBackClick -> appNavigator.popBackStack()
            TripDetailsEvent.OnAutoRefresh -> refreshTripData(isUserRefresh = false)
            TripDetailsEvent.OnUserRefresh -> refreshTripData(isUserRefresh = true)
            TripDetailsEvent.OnEditClick -> {
                val content = _state.value as? TripDetailsState.Content
                if (content?.isOwner == true) {
                    appNavigator.navigate(Destination.EditTrip(tripId))
                }
            }
            TripDetailsEvent.OnInviteTravelersClick -> appNavigator.navigate(
                Destination.InviteTravelers(
                    tripId
                )
            )
            TripDetailsEvent.OnMembersClick -> appNavigator.navigate(
                Destination.TripMembers(tripId)
            )

            TripDetailsEvent.OnWeatherCityClick -> appNavigator.navigate(
                Destination.TripForecast(
                    tripId
                )
            )
            TripDetailsEvent.OnViewForecastClick -> appNavigator.navigate(
                Destination.TripForecast(
                    tripId
                )
            )

            TripDetailsEvent.OnViewItineraryClick -> appNavigator.navigate(
                Destination.TripItinerary(
                    tripId
                )
            )

            TripDetailsEvent.OnBrowseIdeasClick -> appNavigator.navigate(
                Destination.TripIdeas(
                    tripId
                )
            )

            TripDetailsEvent.OnIdeasClick -> appNavigator.navigate(Destination.TripIdeas(tripId))
            TripDetailsEvent.OnExpensesClick -> appNavigator.navigate(Destination.Expenses(tripId))
            TripDetailsEvent.OnPrimaryCtaClick -> {
                val content = _state.value as? TripDetailsState.Content ?: return
                if (content.isEmpty) appNavigator.navigate(Destination.BuildRoute(tripId))
                else appNavigator.navigate(Destination.BuildRoute(tripId))
            }
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(TripDetailsEffect.ShowToastRes(resId)) }
    }

    private fun observeCachedTripData() {
        viewModelScope.launch {
            val membersFlow = runCatching { tripRepository.tripMembers(tripId) }
                .getOrElse { flowOf(emptyList()) }
            val tripFlow = tripRepository.trips.map { trips ->
                trips.firstOrNull { it.id == tripId }
            }
            combine(
                tripFlow,
                membersFlow,
                ideaRepository.observeIdeas(tripId),
                expenseRepository.observeExpenses(tripId),
                userRepository.me,
            ) { trip, members, ideas, expenses, me ->
                if (trip == null) {
                    null
                } else {
                    LoadedTrip(
                        trip = trip,
                        members = members,
                        ideas = ideas,
                        expenses = expenses,
                        isOwner = me?.id == trip.ownerId,
                    )
                }
            }.collect { loaded ->
                if (loaded == null) return@collect
                val current = _state.value as? TripDetailsState.Content
                _state.value = buildState(loaded, latestWeather)
                    .copy(isRefreshing = current?.isRefreshing ?: false)
                AppLogger.i(TAG, "trip details state updated for tripId=$tripId")
            }
        }
    }

    private fun primeTripCache() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    tripRepository.getTrip(tripId).first()
                }
            }.onFailure { error ->
                AppLogger.w(TAG, "primeTripCache failed for tripId=$tripId", error)
            }
        }
    }

    private fun refreshTripData(isUserRefresh: Boolean) {
        viewModelScope.launch {
            if (isUserRefresh) {
                val current = _state.value as? TripDetailsState.Content
                if (current != null) {
                    _state.value = current.copy(isRefreshing = true)
                }
            }
            val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    // Best-effort: list refresh may fail while direct trip fetch still works.
                    runCatching { tripRepository.refreshTrips().getOrThrow() }
                        .onFailure {
                            AppLogger.w(
                                TAG,
                                "refreshTrips failed for tripId=$tripId",
                                it
                            )
                        }
                    // Mandatory: details screen depends on this trip being in cache.
                    val trip = tripRepository.getTrip(tripId).first()
                    runCatching { tripRepository.tripMembers(tripId).first() }
                        .onFailure {
                            AppLogger.w(
                                TAG,
                                "tripMembers refresh failed for tripId=$tripId",
                                it
                            )
                        }
                    runCatching { ideaRepository.refreshIdeas(tripId).getOrThrow() }
                        .onFailure {
                            AppLogger.w(
                                TAG,
                                "refreshIdeas failed for tripId=$tripId",
                                it
                            )
                        }
                    runCatching { expenseRepository.refreshExpenses(tripId).getOrThrow() }
                        .onFailure {
                            AppLogger.w(
                                TAG,
                                "refreshExpenses failed for tripId=$tripId",
                                it
                            )
                        }
                    runCatching { itineraryRepository.refreshItinerary(tripId).getOrThrow() }
                        .onFailure {
                            AppLogger.w(
                                TAG,
                                "refreshItinerary failed for tripId=$tripId",
                                it
                            )
                        }
                    loadWeatherCard(trip)
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    latestWeather = result.data
                    val current = _state.value as? TripDetailsState.Content
                    if (current != null) {
                        _state.value = current.copy(
                            weather = result.data,
                            isRefreshing = false,
                        )
                    }
                    AppLogger.i(TAG, "refreshTripData success for tripId=$tripId")
                }

                is ApiResult.Failure -> {
                    val current = _state.value as? TripDetailsState.Content
                    if (current != null) {
                        _state.value = current.copy(isRefreshing = false)
                    }
                    AppLogger.w(
                        TAG,
                        "refreshTripData failed for tripId=$tripId code=${result.httpCode} apiCode=${result.error?.code.orEmpty()}",
                        result.cause
                    )
                    emitToast(uiErrorMapper.messageRes(result))
                }
            }
        }
    }

    private suspend fun loadWeatherCard(trip: TripDto): WeatherCardUi {
        val itinerary = itineraryRepository.getItinerary(trip.id).first()
        val selectedCity = TripDetailsWeatherMapper.pickCity(itinerary)
            ?: return TripDetailsWeatherMapper.cityMissingCard()

        val start = trip.startDate
        val end = trip.endDate
        val refreshResult = weatherRepository.refreshWeather(
            tripId = trip.id,
            city = selectedCity,
            start = start,
            end = end,
        )
        if (refreshResult.isFailure) {
            AppLogger.w(TAG, "refreshWeather failed for tripId=${trip.id}, city=$selectedCity", refreshResult.exceptionOrNull())
        }

        val response = withTimeoutOrNull(1_000) {
            weatherRepository.getWeather(
                tripId = trip.id,
                city = selectedCity,
                start = start,
                end = end,
            ).first()
        } ?: return TripDetailsWeatherMapper.unavailableCard(selectedCity)

        return TripDetailsWeatherMapper.mapResponse(
            city = selectedCity,
            response = response,
        )
    }
}

private const val TAG = "TripDetailsVM"

private data class LoadedTrip(
    val trip: TripDto,
    val members: List<MemberDto>,
    val ideas: List<IdeaDto>,
    val expenses: List<ExpenseDto>,
    val isOwner: Boolean,
)

private fun buildState(loaded: LoadedTrip, weather: WeatherCardUi): TripDetailsState.Content {
    val trip = loaded.trip
    val start = LocalDate.parse(trip.startDate)
    val end = LocalDate.parse(trip.endDate)
    val dateRange = formatRange(start, end)
    val peopleCount = loaded.members.size
    val avatars = loaded.members.map { member ->
        AvatarStackItem(
            initials = member.initials,
            photoUrl = member.photoUrl
        )
    }
    val ideasCount = loaded.ideas.size
    val totalExpenses = loaded.expenses.sumOf { it.amount }
    val currencySymbol = trip.currencyCode.toCurrency().symbol
    val expensesAmount = if (totalExpenses == 0.0) "${currencySymbol}0" else "$currencySymbol${"%.2f".format(totalExpenses)}"

    val isEmpty = ideasCount == 0 && totalExpenses == 0.0
    val ideasSubtitle = if (ideasCount == 0) "Add your first idea" else ""
    val expensesSubtitle = if (totalExpenses == 0.0) "Track shared expenses" else ""

    val peopleText = if (peopleCount == 1) "1 person" else "$peopleCount people"

    return TripDetailsState.Content(
        isEmpty = isEmpty,
        isOwner = loaded.isOwner,
        header = TripHeaderUi(
            tripId = trip.id,
            title = trip.title,
            dateRange = dateRange,
            locationLine = trip.locationLine.orEmpty(),
            coverUrl = trip.coverUrl,
        ),
        travelers = avatars,
        peopleCountText = peopleText,
        weather = WeatherCardUi(
            city = weather.city,
            days = weather.days,
            notice = weather.notice,
        ),
        nextInTrip = NextInTripUi(
            subtitle = "",
            lines = emptyList(),
        ),
        overview = TripOverviewUi(
            ideasCount = ideasCount,
            ideasSubtitle = ideasSubtitle,
            expensesAmount = expensesAmount,
            expensesSubtitle = expensesSubtitle,
        )
    )
}

private fun pickWeatherCity(days: List<ItineraryDayDto>): String? {
    return days
        .sortedBy { it.dayNumber }
        .firstOrNull { !it.city.isNullOrBlank() && it.cityLat != null && it.cityLon != null }
        ?.city
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private fun buildTempText(tempMin: Double?, tempMax: Double?): String {
    return when {
        tempMin != null && tempMax != null -> "${tempMin.roundTemp()}°/${tempMax.roundTemp()}°"
        tempMax != null -> "${tempMax.roundTemp()}°"
        tempMin != null -> "${tempMin.roundTemp()}°"
        else -> "—"
    }
}

private fun Double.roundTemp(): Int = kotlin.math.round(this).toInt()

private fun formatRange(start: LocalDate, end: LocalDate): String {
    val locale = Locale.getDefault()
    val sameYear = start.year == end.year
    val startFormat = if (sameYear) "MMM d" else "MMM d, yyyy"
    val endFormat = "MMM d, yyyy"
    val startText = start.format(DateTimeFormatter.ofPattern(startFormat, locale))
    val endText = end.format(DateTimeFormatter.ofPattern(endFormat, locale))
    return "$startText – $endText"
}

private fun String.toCurrency(): TripCurrency {
    return TripCurrency.entries.firstOrNull { it.code == this } ?: TripCurrency.EUR
}
