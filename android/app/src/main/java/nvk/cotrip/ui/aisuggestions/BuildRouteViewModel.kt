package nvk.cotrip.ui.aisuggestions

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.ui.common.TextInputLimits
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class BuildRouteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val appNavigator: AppNavigator,
    private val itineraryRepository: ItineraryRepository,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle[Destination.BuildRoute.ARG_TRIP_ID])

    private var citiesFromItinerary =
        appContext.resources.getStringArray(R.array.ai_suggestions_default_cities).toList()

    private val _state = MutableStateFlow(
        BuildRouteState(
            tripId = tripId,
            city = null,
            description = "",
            isDescriptionTooLong = false,
            typeOptions = createAiOptions(R.array.ai_suggestions_option_types),
            timeOfDayOptions = createAiOptions(R.array.ai_suggestions_option_times),
            budgetOptions = createAiOptions(R.array.ai_suggestions_option_budgets),
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

            is BuildRouteEvent.OnDescriptionChange -> _state.update {
                it.copy(
                    description = event.value,
                    isDescriptionTooLong = event.value.length > TextInputLimits.AI_ROUTE_DESCRIPTION,
                )
            }
            is BuildRouteEvent.OnTypeToggle -> toggleType(event.label)
            is BuildRouteEvent.OnTimeOfDaySelect -> selectTimeOfDay(event.label)
            is BuildRouteEvent.OnBudgetSelect -> selectBudget(event.label)
            BuildRouteEvent.OnGenerateClick -> {
                val current = _state.value
                if (!current.canGenerate) return
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
                timeOfDayOptions = toggleSingleSelect(current.timeOfDayOptions, label)
            )
        }
    }

    private fun selectBudget(label: String) {
        _state.update { current ->
            current.copy(
                budgetOptions = toggleSingleSelect(current.budgetOptions, label)
            )
        }
    }

    private fun toggleSingleSelect(options: List<AiOptionUi>, label: String): List<AiOptionUi> {
        val selectedOption = options.firstOrNull { it.label == label }
        val shouldClearSelection = selectedOption?.selected == true
        return options.map { option ->
            option.copy(selected = if (shouldClearSelection) false else option.label == label)
        }
    }

    private fun selectedLabels(options: List<AiOptionUi>): List<String> {
        return options.filter { it.selected }.map { it.label }
    }

    private fun createAiOptions(@ArrayRes arrayResId: Int): List<AiOptionUi> {
        return appContext.resources.getStringArray(arrayResId).map { label ->
            AiOptionUi(label, false)
        }
    }

    private fun loadCitiesFromItinerary() {
        viewModelScope.launch {
            val cities = runCatching {
                itineraryRepository.getItinerary(tripId).first()
                    .mapNotNull { it.city?.trim()?.takeIf { city -> city.isNotBlank() } }
                    .distinct()
            }.getOrNull().orEmpty()

            if (cities.isNotEmpty()) {
                citiesFromItinerary = cities
            }
        }
    }
}
