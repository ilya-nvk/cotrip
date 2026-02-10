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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.repository.ExpenseRepository
import nvk.cotrip.data.repository.IdeaRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TripDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val ideaRepository: IdeaRepository,
    private val expenseRepository: ExpenseRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])

    private val _state =
        MutableStateFlow(createPlaceholderState(tripId))
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripDetailsEffect>()
    val effects = _effects.asSharedFlow()

    init {
        loadTrip(isUserRefresh = false)
    }

    fun onEvent(event: TripDetailsEvent) {
        when (event) {
            TripDetailsEvent.OnBackClick -> appNavigator.popBackStack()
            TripDetailsEvent.OnAutoRefresh -> loadTrip(isUserRefresh = false)
            TripDetailsEvent.OnUserRefresh -> loadTrip(isUserRefresh = true)
            TripDetailsEvent.OnEditClick -> appNavigator.navigate(Destination.EditTrip(tripId))
            TripDetailsEvent.OnInviteTravelersClick -> appNavigator.navigate(
                Destination.InviteTravelers(
                    tripId
                )
            )
            TripDetailsEvent.OnMembersClick -> appNavigator.navigate(
                Destination.TripMembers(tripId)
            )

            TripDetailsEvent.OnWeatherCityClick -> emitToast(R.string.trip_details_city_picker_stub)
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
                if (_state.value.isEmpty) appNavigator.navigate(Destination.BuildRoute(tripId))
                else appNavigator.navigate(Destination.BuildRoute(tripId))
            }
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(TripDetailsEffect.ShowToastRes(resId)) }
    }

    private fun createPlaceholderState(tripId: String): TripDetailsState {
        return TripDetailsState(
            isEmpty = true,
            header = TripHeaderUi(
                tripId = tripId,
                title = "",
                dateRange = "",
                locationLine = ""
            ),
            travelers = emptyList(),
            peopleCountText = "0 people",
            weather = WeatherCardUi(
                city = "",
                days = emptyList()
            ),
            nextInTrip = NextInTripUi(
                subtitle = "",
                lines = emptyList()
            ),
            overview = TripOverviewUi(
                ideasCount = 0,
                ideasSubtitle = "",
                expensesAmount = "",
                expensesSubtitle = ""
            ),
            isRefreshing = false,
        )
    }

    private fun loadTrip(isUserRefresh: Boolean) {
        viewModelScope.launch {
            if (isUserRefresh) {
                _state.value = _state.value.copy(isRefreshing = true)
            }
            val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    val trip = tripRepository.getTrip(tripId)
                    val members = tripRepository.listMembers(tripId)
                    val ideas = ideaRepository.listIdeas(tripId)
                    val expenses = expenseRepository.listExpenses(tripId)
                    LoadedTrip(trip, members, ideas, expenses)
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    _state.value = buildState(result.data)
                }

                is ApiResult.Failure -> {
                    _state.value = _state.value.copy(isRefreshing = false)
                    emitToast(uiErrorMapper.messageRes(result))
                    if (_state.value.header.title.isBlank()) {
                        appNavigator.popBackStack()
                    }
                }
            }
        }
    }
}

private data class LoadedTrip(
    val trip: TripDto,
    val members: List<MemberDto>,
    val ideas: List<IdeaDto>,
    val expenses: List<ExpenseDto>,
)

private fun buildState(loaded: LoadedTrip): TripDetailsState {
    val trip = loaded.trip
    val start = LocalDate.parse(trip.startDate)
    val end = LocalDate.parse(trip.endDate)
    val dateRange = formatRange(start, end)
    val peopleCount = loaded.members.size
    val initials = loaded.members.map { it.initials }
    val ideasCount = loaded.ideas.size
    val totalExpenses = loaded.expenses.sumOf { it.amount }
    val currencySymbol = trip.currencyCode.toCurrency().symbol
    val expensesAmount = if (totalExpenses == 0.0) "${currencySymbol}0" else "$currencySymbol${"%.2f".format(totalExpenses)}"

    val isEmpty = ideasCount == 0 && totalExpenses == 0.0
    val ideasSubtitle = if (ideasCount == 0) "Add your first idea" else ""
    val expensesSubtitle = if (totalExpenses == 0.0) "Track shared expenses" else ""

    val peopleText = if (peopleCount == 1) "1 person" else "$peopleCount people"

    return TripDetailsState(
        isEmpty = isEmpty,
        header = TripHeaderUi(
            tripId = trip.id,
            title = trip.title,
            dateRange = dateRange,
            locationLine = trip.locationLine.orEmpty(),
        ),
        travelers = initials,
        peopleCountText = peopleText,
        weather = WeatherCardUi(
            city = trip.locationLine?.split(",")?.firstOrNull()?.trim().orEmpty(),
            days = emptyList(),
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
