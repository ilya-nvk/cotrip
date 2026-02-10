package nvk.cotrip.ui.aisuggestions

import android.net.Uri
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
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.AiSuggestionsRequestDto
import nvk.cotrip.data.repository.AiSuggestionsRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class RouteSuggestionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val aiSuggestionsRepository: AiSuggestionsRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.RouteSuggestions.ARG_TRIP_ID])
    private val selectedCity: String = decodeArg(savedStateHandle[Destination.RouteSuggestions.ARG_CITY])
        ?.takeIf { it.isNotBlank() }
        ?: "Trip city"
    private val description: String? = decodeArg(savedStateHandle[Destination.RouteSuggestions.ARG_DESCRIPTION])
        ?.takeIf { it.isNotBlank() }
    private val selectedTypes: List<String> =
        decodeCsvArg(savedStateHandle[Destination.RouteSuggestions.ARG_TYPE_OPTIONS])
    private val selectedTimes: List<String> =
        decodeCsvArg(savedStateHandle[Destination.RouteSuggestions.ARG_TIME_OF_DAY_OPTIONS])
    private val selectedBudgets: List<String> =
        decodeCsvArg(savedStateHandle[Destination.RouteSuggestions.ARG_BUDGET_OPTIONS])

    private val _effects = MutableSharedFlow<RouteSuggestionsEffect>()
    val effects = _effects.asSharedFlow()

    private val _state = MutableStateFlow(
        RouteSuggestionsState(
            tripId = tripId,
            city = selectedCity,
            subtitle = buildSubtitle(selectedTypes, selectedTimes, selectedBudgets),
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

            is RouteSuggestionsEvent.OnSaveClick -> saveSuggestion(event.suggestionId)
        }
    }

    private fun regenerateSuggestions() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = apiCaller.call {
                aiSuggestionsRepository.generateSuggestions(
                    tripId = tripId,
                    request = AiSuggestionsRequestDto(
                        city = selectedCity,
                        description = description,
                        typeOptions = selectedTypes,
                        timeOfDayOptions = selectedTimes,
                        budgetOptions = selectedBudgets,
                    )
                )
            }

            when (result) {
                is ApiResult.Success -> {
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            suggestions = result.data.map { suggestion ->
                                AiSuggestionItemUi(
                                    id = suggestion.id,
                                    title = suggestion.title,
                                    description = suggestion.description.orEmpty(),
                                    typeLabel = suggestion.typeLabel.orEmpty().ifBlank { "Activity" },
                                    durationLabel = suggestion.durationLabel.orEmpty().ifBlank { "2-3h" },
                                    budgetLabel = suggestion.budgetLabel.orEmpty().ifBlank { "Any" },
                                    estimatedCost = formatEstimatedCost(suggestion.estimatedCost),
                                    isSaved = suggestion.isSaved,
                                )
                            },
                        )
                    }
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false) }
                    emit(RouteSuggestionsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun saveSuggestion(suggestionId: String) {
        if (_state.value.isLoading) return
        val currentItem = _state.value.suggestions.firstOrNull { it.id == suggestionId } ?: return
        if (currentItem.isSaved) return

        viewModelScope.launch {
            val result = apiCaller.call {
                aiSuggestionsRepository.saveSuggestionToIdeas(suggestionId)
            }
            when (result) {
                is ApiResult.Success -> {
                    _state.update { current ->
                        current.copy(
                            suggestions = current.suggestions.map { suggestion ->
                                if (suggestion.id == suggestionId) {
                                    suggestion.copy(isSaved = true)
                                } else {
                                    suggestion
                                }
                            }
                        )
                    }
                    emit(RouteSuggestionsEffect.ShowToastRes(R.string.ai_suggestions_saved))
                }

                is ApiResult.Failure -> {
                    emit(RouteSuggestionsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun emit(effect: RouteSuggestionsEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private fun decodeArg(raw: String?): String? {
        return raw?.let { Uri.decode(it) }?.trim()
    }

    private fun decodeCsvArg(raw: String?): List<String> {
        val decoded = decodeArg(raw).orEmpty()
        if (decoded.isBlank()) return emptyList()
        return decoded
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun buildSubtitle(
        selectedTypes: List<String>,
        selectedTimes: List<String>,
        selectedBudgets: List<String>,
    ): String {
        val allFilters = selectedTypes + selectedTimes + selectedBudgets
        if (allFilters.isEmpty()) return "All types"
        return allFilters.joinToString(" • ")
    }

    private fun formatEstimatedCost(cost: Double?): String {
        if (cost == null) return "—"
        return if (cost % 1.0 == 0.0) {
            cost.toInt().toString()
        } else {
            String.format(Locale.US, "%.2f", cost)
        }
    }
}
