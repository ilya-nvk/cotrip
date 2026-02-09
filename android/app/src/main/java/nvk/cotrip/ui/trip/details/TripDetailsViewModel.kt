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
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.Info
import nvk.cotrip.ui.theme.Success
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.Warning
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TripDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val api: CoTripApi,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])

    private val _state =
        MutableStateFlow(createPlaceholderState(tripId))
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripDetailsEffect>()
    val effects = _effects.asSharedFlow()

    init {
        loadTrip()
    }

    fun onEvent(event: TripDetailsEvent) {
        when (event) {
            TripDetailsEvent.OnBackClick -> appNavigator.popBackStack()
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
            )
        )
    }

    private fun loadTrip() {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val trip = api.getTrip(tripId)
                    val members = api.listMembers(tripId).items
                    val ideas = api.listIdeas(tripId).items
                    val expenses = api.listExpenses(tripId).items
                    LoadedTrip(trip, members, ideas, expenses)
                }
            }

            result.onSuccess { loaded ->
                _state.value = buildState(loaded)
            }.onFailure {
                emitToast(R.string.common_error_message)
                appNavigator.popBackStack()
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
