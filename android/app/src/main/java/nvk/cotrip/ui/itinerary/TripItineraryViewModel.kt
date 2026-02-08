package nvk.cotrip.ui.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class TripItineraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.TripItinerary.ARG_TRIP_ID])

    private val cities = listOf(
        "Paris",
        "Rome",
        "Barcelona",
        "London",
        "Berlin",
        "Amsterdam",
        "Prague",
        "Vienna",
        "Lisbon",
        "Madrid",
        "Florence",
        "Milan",
        "Munich",
        "Copenhagen",
        "Stockholm",
        "Oslo",
        "Helsinki",
        "Zurich",
        "Geneva",
        "Nice",
        "Venice",
        "Athens",
        "Budapest",
        "Brussels",
        "Dublin",
    )

    private val _state = MutableStateFlow(
        TripItineraryState(
            tripId = tripId,
            dateRange = "Jul 15 - Jul 29, 2026",
            mode = ItineraryMode.Filled,
            days = listOf(
                ItineraryDayUi(
                    id = "d1",
                    dayNumber = 1,
                    dateText = "Thu, Jul 15",
                    city = "Paris",
                    activities = listOf(
                        ItineraryActivityUi(
                            "a1",
                            "10:00",
                            "Arrive at Charles de Gaulle Airport",
                            "Charles de Gaulle Airport",
                            null
                        ),
                        ItineraryActivityUi(
                            "a2",
                            "12:30",
                            "Check-in at hotel in Le Marais",
                            "Hôtel du Petit Moulin",
                            null
                        ),
                        ItineraryActivityUi(
                            "a3",
                            "15:00",
                            "Walk around Le Marais district",
                            "Le Marais",
                            null
                        ),
                        ItineraryActivityUi(
                            "a4",
                            "19:00",
                            "Dinner at Le Comptoir du Relais",
                            "Le Comptoir du Relais",
                            "€45"
                        ),
                    )
                ),
                ItineraryDayUi(
                    id = "d2",
                    dayNumber = 2,
                    dateText = "Fri, Jul 16",
                    city = "Paris",
                    activities = listOf(
                        ItineraryActivityUi(
                            "a5",
                            "09:00",
                            "Visit the Louvre Museum",
                            "Louvre Museum",
                            "€17"
                        ),
                        ItineraryActivityUi(
                            "a6",
                            "13:00",
                            "Lunch near the museum",
                            "Café Marly",
                            "€30"
                        ),
                        ItineraryActivityUi(
                            "a7",
                            "15:00",
                            "Walk along the Seine",
                            "Seine River",
                            null
                        ),
                        ItineraryActivityUi(
                            "a8",
                            "17:00",
                            "Eiffel Tower visit",
                            "Eiffel Tower",
                            "€26"
                        ),
                        ItineraryActivityUi(
                            "a9",
                            "20:00",
                            "Seine river cruise",
                            "Bateaux Parisiens",
                            null
                        ),
                    )
                ),
            ),
            cityPicker = null
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripItineraryEffect>()
    val effects = _effects.asSharedFlow()

    fun onEvent(event: TripItineraryEvent) {
        when (event) {
            TripItineraryEvent.OnBackClick -> appNavigator.popBackStack()
            TripItineraryEvent.OnAddActivityClick -> appNavigator.navigate(
                Destination.CreateActivity(
                    tripId
                )
            )
            TripItineraryEvent.OnDismissCityPicker -> _state.update { it.copy(cityPicker = null) }
            is TripItineraryEvent.OnActivityClick -> appNavigator.navigate(
                Destination.ActivityDetails(event.activityId)
            )

            is TripItineraryEvent.OnChooseCityClick -> openCityPicker(event.dayId)
            is TripItineraryEvent.OnCityQueryChange -> updateCityQuery(event.value)
            is TripItineraryEvent.OnCitySelected -> selectCity(event.city)
        }
    }

    private fun openCityPicker(dayId: String) {
        _state.update { st ->
            st.copy(
                cityPicker = CityPickerState(
                    dayId = dayId,
                    query = "",
                    allCities = cities,
                    filteredCities = cities
                )
            )
        }
    }

    private fun updateCityQuery(value: String) {
        _state.update { st ->
            val picker = st.cityPicker ?: return@update st
            val filtered = picker.allCities.filter { it.contains(value, ignoreCase = true) }
            st.copy(cityPicker = picker.copy(query = value, filteredCities = filtered))
        }
    }

    private fun selectCity(city: String) {
        _state.update { st ->
            val picker = st.cityPicker ?: return@update st
            val days = st.days.map { d ->
                if (d.id == picker.dayId) d.copy(city = city) else d
            }
            st.copy(days = days, cityPicker = null)
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(TripItineraryEffect.ShowToastRes(resId)) }
    }
}
