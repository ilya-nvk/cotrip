package nvk.cotrip.ui.forecast

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
import nvk.cotrip.ui.navigation.ARG_TRIP_ID
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.Info
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.Warning
import javax.inject.Inject


@HiltViewModel
class TripForecastViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle[ARG_TRIP_ID])

    private val _state = MutableStateFlow(
        TripForecastState(
            city = "Paris",
            days = listOf(
                ForecastDayUi(
                    "Today",
                    "Thu, Jul 15",
                    CoTripIcons.Info,
                    Warning,
                    "26°",
                    "Mostly sunny"
                ),
                ForecastDayUi("Tomorrow", "Fri, Jul 16", CoTripIcons.Info, Warning, "28°", "Sunny"),
                ForecastDayUi(
                    "Sat, Jul 17",
                    "",
                    CoTripIcons.Info,
                    TextSecondary,
                    "24°",
                    "Partly cloudy"
                ),
                ForecastDayUi("Sun, Jul 18", "", CoTripIcons.Info, Info, "22°", "Light rain"),
                ForecastDayUi("Mon, Jul 19", "", CoTripIcons.Info, TextSecondary, "23°", "Cloudy"),
                ForecastDayUi("Tue, Jul 20", "", CoTripIcons.Info, Warning, "27°", "Sunny"),
                ForecastDayUi("Wed, Jul 21", "", CoTripIcons.Info, Warning, "29°", "Clear sky"),
                ForecastDayUi("Thu, Jul 22", "", CoTripIcons.Info, Warning, "30°", "Hot and sunny"),
                ForecastDayUi(
                    "Fri, Jul 23",
                    "",
                    CoTripIcons.Info,
                    TextSecondary,
                    "25°",
                    "Partly cloudy"
                ),
                ForecastDayUi("Sat, Jul 24", "", CoTripIcons.Info, Info, "21°", "Rain showers"),
                ForecastDayUi(
                    "Sun, Jul 25",
                    "",
                    CoTripIcons.Info,
                    TextSecondary,
                    "23°",
                    "Overcast"
                ),
                ForecastDayUi("Mon, Jul 26", "", CoTripIcons.Info, Warning, "26°", "Mostly sunny"),
                ForecastDayUi("Tue, Jul 27", "", CoTripIcons.Info, Warning, "28°", "Sunny"),
                ForecastDayUi("Wed, Jul 28", "", CoTripIcons.Info, Warning, "27°", "Clear"),
                ForecastDayUi(
                    "Thu, Jul 29",
                    "",
                    CoTripIcons.Info,
                    TextSecondary,
                    "24°",
                    "Partly cloudy"
                ),
            ),
            source = "OpenWeather",
            lastUpdated = "Jan 30, 2026"
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripForecastEffect>()
    val effects = _effects.asSharedFlow()

    fun onEvent(event: TripForecastEvent) {
        when (event) {
            TripForecastEvent.OnBackClick -> appNavigator.popBackStack()
            TripForecastEvent.OnCityClick -> emitToast(R.string.weather_forecast_city_not_implemented)
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(TripForecastEffect.ShowToastRes(resId)) }
    }
}