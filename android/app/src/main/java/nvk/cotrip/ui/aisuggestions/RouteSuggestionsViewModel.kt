package nvk.cotrip.ui.aisuggestions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class RouteSuggestionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.RouteSuggestions.ARG_TRIP_ID])

    private var refreshCounter: Int = 0
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(
        RouteSuggestionsState(
            tripId = tripId,
            city = "Paris",
            subtitle = "All types",
            isLoading = true,
            suggestions = emptyList()
        )
    )
    val state = _state.asStateFlow()

    init {
        regenerateSuggestions()
    }

    fun onEvent(event: RouteSuggestionsEvent) {
        when (event) {
            RouteSuggestionsEvent.OnBackClick -> appNavigator.popBackStack()
            RouteSuggestionsEvent.OnRefreshClick -> regenerateSuggestions()
            RouteSuggestionsEvent.OnChangeFiltersClick -> appNavigator.navigate(
                Destination.BuildRoute(
                    tripId
                )
            )

            is RouteSuggestionsEvent.OnSaveClick -> _state.update { current ->
                current.copy(
                    suggestions = current.suggestions.map { suggestion ->
                        if (suggestion.id == event.suggestionId) {
                            suggestion.copy(isSaved = !suggestion.isSaved)
                        } else {
                            suggestion
                        }
                    }
                )
            }
        }
    }

    private fun regenerateSuggestions() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            delay(1200)
            refreshCounter += 1

            val isFilteredVariant = refreshCounter % 2 == 1

            _state.update {
                it.copy(
                    subtitle = if (isFilteredVariant) "food • evening • €€" else "All types",
                    isLoading = false,
                    suggestions = if (isFilteredVariant) filteredSuggestions() else allTypeSuggestions(),
                )
            }
        }
    }

    private fun filteredSuggestions(): List<AiSuggestionItemUi> {
        return listOf(
            AiSuggestionItemUi(
                id = "sg1",
                title = "Louvre Museum",
                description = "Explore the world's largest art museum and see iconic works like the Mona Lisa.",
                typeLabel = "Museum",
                durationLabel = "3-4h",
                budgetLabel = "€€",
                estimatedCost = "€17",
                isSaved = true
            ),
            AiSuggestionItemUi(
                id = "sg2",
                title = "Eiffel Tower Summit",
                description = "Take the elevator to the top for breathtaking panoramic views of Paris.",
                typeLabel = "Must-see",
                durationLabel = "2-3h",
                budgetLabel = "€€",
                estimatedCost = "€28",
                isSaved = false
            ),
            AiSuggestionItemUi(
                id = "sg3",
                title = "Seine River Cruise",
                description = "Relax on a scenic boat tour passing by famous landmarks along the Seine.",
                typeLabel = "Nature",
                durationLabel = "1h",
                budgetLabel = "€",
                estimatedCost = "€15",
                isSaved = false
            ),
        )
    }

    private fun allTypeSuggestions(): List<AiSuggestionItemUi> {
        return filteredSuggestions().map { it.copy(isSaved = false) }
    }
}
