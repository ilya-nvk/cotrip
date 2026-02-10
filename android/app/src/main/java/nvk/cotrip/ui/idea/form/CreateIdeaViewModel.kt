package nvk.cotrip.ui.idea.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.repository.IdeaRepository
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import javax.inject.Inject

@HiltViewModel
class CreateIdeaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val ideaRepository: IdeaRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel(), IdeaFormContract {

    private val tripId: String =
        checkNotNull(savedStateHandle.get<String>(Destination.CreateIdea.ARG_TRIP_ID))

    private var citySearchJob: Job? = null

    private val _state = MutableStateFlow(
        IdeaFormState(
            mode = IdeaFormMode.Create,
            ideaId = null,
            title = "",
            city = "",
            cityPlaceId = null,
            citySuggestions = emptyList(),
            isCitySearching = false,
            currencySymbol = "€",
            costAmount = "",
            costType = IdeaCostType.PerPerson,
            website = "",
            notes = "",
            isSaving = false,
        )
    )
    override val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<IdeaFormEffect>()
    override val effects = _effects.asSharedFlow()

    init {
        loadTripMeta()
    }

    override fun onEvent(event: IdeaFormEvent) {
        when (event) {
            IdeaFormEvent.OnBackClick -> appNavigator.popBackStack()
            IdeaFormEvent.OnPrimaryClick -> createIdea()
            IdeaFormEvent.OnDeleteClick -> emit(IdeaFormEffect.ShowToastRes(R.string.idea_form_delete_not_available))
            is IdeaFormEvent.OnCitySelected -> onCitySuggestionSelected(event.city)

            is IdeaFormEvent.OnTitleChange -> _state.update { it.copy(title = event.value) }
            is IdeaFormEvent.OnCityChange -> onCityInputChanged(event.value)
            is IdeaFormEvent.OnCostAmountChange -> _state.update {
                it.copy(costAmount = event.value.filter { c -> c.isDigit() || c == '.' || c == ',' })
            }

            is IdeaFormEvent.OnCostTypeChange -> _state.update { it.copy(costType = event.value) }
            is IdeaFormEvent.OnWebsiteChange -> _state.update { it.copy(website = event.value) }
            is IdeaFormEvent.OnNotesChange -> _state.update { it.copy(notes = event.value) }
        }
    }

    private fun loadTripMeta() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    val trip = tripRepository.getTrip(tripId)
                    TripMeta(
                        currencySymbol = currencySymbolFor(trip.currencyCode)
                    )
                }
            }) {
                is ApiResult.Success -> {
                    val meta = result.data
                    _state.update { it.copy(currencySymbol = meta.currencySymbol) }
                }

                is ApiResult.Failure -> emit(
                    IdeaFormEffect.ShowToastRes(
                        uiErrorMapper.messageRes(
                            result
                        )
                    )
                )
            }
        }
    }

    private fun onCityInputChanged(value: String) {
        val query = value.trim()
        citySearchJob?.cancel()
        _state.update {
            it.copy(
                city = value,
                cityPlaceId = null,
                citySuggestions = emptyList(),
                isCitySearching = query.isNotBlank(),
            )
        }

        if (query.isBlank()) return

        citySearchJob = viewModelScope.launch {
            delay(300)
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    itineraryRepository.searchPlaces(tripId = tripId, query = query, limit = 8)
                }
            }) {
                is ApiResult.Success -> {
                    val suggestions = result.data.map {
                        IdeaLocationSuggestionUi(
                            name = it.name,
                            placeId = it.placeId,
                            fullText = it.fullText,
                        )
                    }
                    _state.update { current ->
                        if (current.city.trim() != query) return@update current
                        current.copy(citySuggestions = suggestions, isCitySearching = false)
                    }
                }

                is ApiResult.Failure -> {
                    _state.update { current ->
                        if (current.city.trim() != query) return@update current
                        current.copy(citySuggestions = emptyList(), isCitySearching = false)
                    }
                }
            }
        }
    }

    private fun onCitySuggestionSelected(city: IdeaLocationSuggestionUi) {
        citySearchJob?.cancel()
        _state.update {
            it.copy(
                city = city.fullText,
                cityPlaceId = city.placeId,
                citySuggestions = emptyList(),
                isCitySearching = false,
            )
        }
    }

    private fun createIdea() {
        val snapshot = _state.value
        if (snapshot.title.isBlank()) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    ideaRepository.createIdea(
                        tripId = tripId,
                        request = CreateIdeaRequest(
                            title = snapshot.title.trim(),
                            city = snapshot.city.trim().ifBlank { null },
                            costAmount = parseAmount(snapshot.costAmount),
                            costType = snapshot.costAmount.toCostType(snapshot.costType),
                            website = snapshot.website.trim().ifBlank { null },
                            notes = snapshot.notes.trim().ifBlank { null },
                        )
                    )
                }
            }) {
                is ApiResult.Success -> {
                    emit(IdeaFormEffect.ShowToastRes(R.string.idea_form_created_toast))
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> {
                    emit(IdeaFormEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                    _state.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    private fun emit(effect: IdeaFormEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private data class TripMeta(
        val currencySymbol: String,
    )
}

private fun parseAmount(amount: String): Double? {
    val normalized = amount.replace(',', '.').trim()
    return normalized.toDoubleOrNull()
}

private fun String.toCostType(type: IdeaCostType): String? {
    if (this.replace(',', '.').trim().isBlank()) return null
    return when (type) {
        IdeaCostType.PerPerson -> "per_person"
        IdeaCostType.Total -> "total"
    }
}

private fun currencySymbolFor(code: String): String {
    return TripCurrency.values().firstOrNull { it.code == code }?.symbol ?: code
}
