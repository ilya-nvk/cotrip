package nvk.cotrip.ui.aisuggestions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class BuildRouteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle[Destination.BuildRoute.ARG_TRIP_ID])

    private var citiesFromItinerary = listOf(
        "Paris",
        "Rome",
        "Barcelona",
        "Versailles",
    )

    private val _state = MutableStateFlow(
        BuildRouteState(
            tripId = tripId,
            city = null,
            description = "",
            typeOptions = listOf(
                AiOptionUi("Must-see", false),
                AiOptionUi("Food", false),
                AiOptionUi("Museums", false),
                AiOptionUi("Night", false),
                AiOptionUi("Nature", false),
                AiOptionUi("Budget", false),
                AiOptionUi("Random", false),
            ),
            timeOfDayOptions = listOf(
                AiOptionUi("Morning", false),
                AiOptionUi("Afternoon", false),
                AiOptionUi("Evening", false),
            ),
            budgetOptions = listOf(
                AiOptionUi("Free", false),
                AiOptionUi("€", false),
                AiOptionUi("€€", false),
                AiOptionUi("€€€", false),
            ),
            cityPicker = null
        )
    )
    val state = _state.asStateFlow()

    init {
        loadCitiesFromItinerary()
    }

    fun onEvent(event: BuildRouteEvent) {
        when (event) {
            BuildRouteEvent.OnBackClick -> appNavigator.popBackStack()
            BuildRouteEvent.OnCityClick -> _state.update {
                it.copy(cityPicker = AiCityPickerState(cities = citiesFromItinerary))
            }

            BuildRouteEvent.OnDismissCityPicker -> _state.update { it.copy(cityPicker = null) }
            is BuildRouteEvent.OnCitySelected -> _state.update {
                it.copy(city = event.city, cityPicker = null)
            }

            is BuildRouteEvent.OnDescriptionChange -> _state.update { it.copy(description = event.value) }
            is BuildRouteEvent.OnTypeToggle -> toggleType(event.label)
            is BuildRouteEvent.OnTimeOfDaySelect -> selectTimeOfDay(event.label)
            is BuildRouteEvent.OnBudgetSelect -> selectBudget(event.label)
            BuildRouteEvent.OnGenerateClick -> {
                val current = _state.value
                val selectedCity = current.city?.trim()?.takeIf { it.isNotBlank() }
                if (selectedCity != null) {
                    appNavigator.navigate(
                        Destination.RouteSuggestions(
                            tripId = tripId,
                            city = selectedCity,
                            description = current.description.trim().takeIf { it.isNotBlank() },
                            typeOptions = selectedLabels(current.typeOptions),
                            timeOfDayOptions = selectedLabels(current.timeOfDayOptions),
                            budgetOptions = selectedLabels(current.budgetOptions),
                        )
                    )
                }
            }
        }
    }

    private fun toggleType(label: String) {
        _state.update { current ->
            current.copy(
                typeOptions = current.typeOptions.map { option ->
                    if (option.label == label) option.copy(selected = !option.selected) else option
                }
            )
        }
    }

    private fun selectTimeOfDay(label: String) {
        _state.update { current ->
            current.copy(
                timeOfDayOptions = current.timeOfDayOptions.map { option ->
                    option.copy(selected = option.label == label)
                }
            )
        }
    }

    private fun selectBudget(label: String) {
        _state.update { current ->
            current.copy(
                budgetOptions = current.budgetOptions.map { option ->
                    option.copy(selected = option.label == label)
                }
            )
        }
    }

    private fun selectedLabels(options: List<AiOptionUi>): List<String> {
        return options.filter { it.selected }.map { it.label }
    }

    private fun loadCitiesFromItinerary() {
        viewModelScope.launch {
            val cities = runCatching {
                itineraryRepository.getItinerary(tripId)
                    .mapNotNull { it.city?.trim()?.takeIf { city -> city.isNotBlank() } }
                    .distinct()
            }.getOrNull().orEmpty()

            if (cities.isNotEmpty()) {
                citiesFromItinerary = cities
            }
        }
    }
}
