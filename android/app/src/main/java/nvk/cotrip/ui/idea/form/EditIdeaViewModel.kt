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
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import nvk.cotrip.data.repository.IdeaRepository
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import javax.inject.Inject

@HiltViewModel
class EditIdeaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val ideaRepository: IdeaRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel(), IdeaFormContract {

    private val tripId: String =
        checkNotNull(savedStateHandle.get<String>(Destination.EditIdea.ARG_TRIP_ID))
    private val ideaId: String =
        checkNotNull(savedStateHandle.get<String>(Destination.EditIdea.ARG_IDEA_ID))

    private var citySearchJob: Job? = null

    private val _state = MutableStateFlow(
        IdeaFormState(
            mode = IdeaFormMode.Edit,
            ideaId = ideaId,
            title = "",
            city = "",
            cityPlaceId = null,
            link = "",
            citySuggestions = emptyList(),
            isCitySearching = false,
            currencySymbol = "€",
            costAmount = "",
            costType = IdeaCostType.PerPerson,
            notes = "",
            isSaving = false,
        )
    )
    override val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<IdeaFormEffect>()
    override val effects = _effects.asSharedFlow()

    init {
        loadIdea()
    }

    override fun onEvent(event: IdeaFormEvent) {
        when (event) {
            IdeaFormEvent.OnBackClick -> appNavigator.popBackStack()
            IdeaFormEvent.OnPrimaryClick -> updateIdea()
            IdeaFormEvent.OnDeleteClick -> deleteIdea()
            is IdeaFormEvent.OnCitySelected -> onCitySuggestionSelected(event.city)
            is IdeaFormEvent.OnTitleChange -> _state.update { it.copy(title = event.value) }
            is IdeaFormEvent.OnCityChange -> onCityInputChanged(event.value)
            is IdeaFormEvent.OnLinkChange -> _state.update { it.copy(link = event.value) }
            is IdeaFormEvent.OnCostAmountChange -> _state.update {
                it.copy(costAmount = event.value.filter { c -> c.isDigit() || c == '.' || c == ',' })
            }

            is IdeaFormEvent.OnCostTypeChange -> _state.update { it.copy(costType = event.value) }
            is IdeaFormEvent.OnNotesChange -> _state.update { it.copy(notes = event.value) }
        }
    }

    private fun loadIdea() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    val idea = ideaRepository.getIdea(ideaId)
                    val trip = tripRepository.getTrip(tripId)
                    LoadedIdea(
                        title = idea.title,
                        city = idea.city.orEmpty(),
                        link = idea.link.orEmpty(),
                        costAmount = idea.costAmount?.let { formatAmount(it) }.orEmpty(),
                        costType = idea.costType.toIdeaCostType(),
                        notes = idea.notes.orEmpty(),
                        currencySymbol = currencySymbolFor(trip.currencyCode),
                    )
                }
            }) {
                is ApiResult.Success -> {
                    val loaded = result.data
                    _state.update {
                        it.copy(
                            title = loaded.title,
                            city = loaded.city,
                            cityPlaceId = null,
                            link = loaded.link,
                            citySuggestions = emptyList(),
                            isCitySearching = false,
                            costAmount = loaded.costAmount,
                            costType = loaded.costType,
                            notes = loaded.notes,
                            currencySymbol = loaded.currencySymbol,
                        )
                    }
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

    private fun updateIdea() {
        val snapshot = _state.value
        if (snapshot.title.isBlank()) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    ideaRepository.updateIdea(
                        ideaId = ideaId,
                        request = UpdateIdeaRequest(
                            title = snapshot.title.trim(),
                            city = snapshot.city.trim().ifBlank { null },
                            link = snapshot.link.trim().ifBlank { null },
                            costAmount = parseAmount(snapshot.costAmount),
                            costType = snapshot.costAmount.toCostType(snapshot.costType),
                            notes = snapshot.notes.trim().ifBlank { null },
                        )
                    )
                }
            }) {
                is ApiResult.Success -> {
                    emit(IdeaFormEffect.ShowToastRes(R.string.idea_form_saved_toast))
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> {
                    emit(IdeaFormEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                    _state.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    private fun deleteIdea() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) { ideaRepository.deleteIdea(ideaId) }
            }) {
                is ApiResult.Success -> {
                    emit(IdeaFormEffect.ShowToastRes(R.string.idea_form_deleted_toast))
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> {
                    emit(IdeaFormEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun emit(effect: IdeaFormEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private data class LoadedIdea(
        val title: String,
        val city: String,
        val link: String,
        val costAmount: String,
        val costType: IdeaCostType,
        val notes: String,
        val currencySymbol: String,
    )
}

private fun String?.toIdeaCostType(): IdeaCostType {
    return when (this) {
        "total" -> IdeaCostType.Total
        else -> IdeaCostType.PerPerson
    }
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

private fun formatAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        amount.toInt().toString()
    } else {
        String.format(java.util.Locale.getDefault(), "%.2f", amount)
    }
}
