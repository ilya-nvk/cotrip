package nvk.cotrip.ui.tripdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.Info
import nvk.cotrip.ui.theme.Success
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.Warning
import javax.inject.Inject

@HiltViewModel
class TripDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])

    private val _state =
        MutableStateFlow(createInitialState(tripId, savedStateHandle["isEmpty"] ?: false))
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripDetailsEffect>()
    val effects = _effects.asSharedFlow()

    fun onEvent(event: TripDetailsEvent) {
        when (event) {
            TripDetailsEvent.OnBackClick -> appNavigator.popBackStack()
            TripDetailsEvent.OnEditClick -> appNavigator.navigate(Destination.EditTrip(tripId))
            TripDetailsEvent.OnInviteTravelersClick -> appNavigator.navigate(
                Destination.InviteTravelers(
                    tripId
                )
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
                else appNavigator.navigate(Destination.RouteSuggestions(tripId))
            }
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(TripDetailsEffect.ShowToastRes(resId)) }
    }

    private fun createInitialState(tripId: String, isEmpty: Boolean): TripDetailsState {
        return if (isEmpty) {
            TripDetailsState(
                isEmpty = true,
                header = TripHeaderUi(
                    tripId = tripId,
                    title = "Summer Europe Trip",
                    dateRange = "Jul 15 – Jul 29, 2026",
                    locationLine = "Paris, Rome, Barcelona"
                ),
                travelers = emptyList(),
                peopleCountText = "0 people",
                weather = WeatherCardUi(
                    city = "Paris",
                    days = emptyList()
                ),
                nextInTrip = NextInTripUi(
                    subtitle = "",
                    lines = emptyList()
                ),
                overview = TripOverviewUi(
                    ideasCount = 0,
                    ideasSubtitle = "Add your first idea",
                    expensesAmount = "$0",
                    expensesSubtitle = "Track shared expenses"
                )
            )
        } else {
            TripDetailsState(
                isEmpty = false,
                header = TripHeaderUi(
                    tripId = tripId,
                    title = "Summer Europe Trip",
                    dateRange = "Jul 15 – Jul 29, 2026",
                    locationLine = "Paris, Rome, Barcelona"
                ),
                travelers = listOf("JD", "SM", "AK", "MR"),
                peopleCountText = "4 people",
                weather = WeatherCardUi(
                    city = "Paris",
                    days = listOf(
                        WeatherDayUi("Today", "26°", CoTripIcons.Info, Info),
                        WeatherDayUi("Fri", "28°", CoTripIcons.Info, Warning),
                        WeatherDayUi("Sat", "24°", CoTripIcons.Info, TextSecondary),
                        WeatherDayUi("Sun", "22°", CoTripIcons.Info, Success),
                        WeatherDayUi("Mon", "23°", CoTripIcons.Info, TextSecondary),
                    )
                ),
                nextInTrip = NextInTripUi(
                    subtitle = "Day 3 · Paris",
                    lines = listOf(
                        "Visit the Louvre Museum",
                        "Lunch at Le Marais",
                        "Evening river cruise"
                    )
                ),
                overview = TripOverviewUi(
                    ideasCount = 12,
                    ideasSubtitle = "",
                    expensesAmount = "$1450",
                    expensesSubtitle = ""
                )
            )
        }
    }
}