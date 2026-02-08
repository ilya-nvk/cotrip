package nvk.cotrip.ui.idea.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import javax.inject.Inject

@HiltViewModel
class EditIdeaViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val api: CoTripApi,
) : ViewModel(), IdeaFormContract {

    private val tripId: String =
        checkNotNull(savedStateHandle.get<String>(Destination.EditIdea.ARG_TRIP_ID))
    private val ideaId: String =
        checkNotNull(savedStateHandle.get<String>(Destination.EditIdea.ARG_IDEA_ID))

    private var availableCities: List<String> = emptyList()

    private val _state = MutableStateFlow(
        IdeaFormState(
            mode = IdeaFormMode.Edit,
            ideaId = ideaId,
            title = "",
            city = "",
            currencySymbol = "€",
            costAmount = "",
            costType = IdeaCostType.PerPerson,
            website = "",
            notes = "",
            isSaving = false,
            cityPicker = null
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
            IdeaFormEvent.OnCityClick -> openCityPicker()
            IdeaFormEvent.OnDismissCityPicker -> _state.update { it.copy(cityPicker = null) }
            is IdeaFormEvent.OnCitySelected -> _state.update {
                it.copy(
                    city = event.city,
                    cityPicker = null
                )
            }
            is IdeaFormEvent.OnTitleChange -> _state.update { it.copy(title = event.value) }
            is IdeaFormEvent.OnCityChange -> _state.update { it.copy(city = event.value) }
            is IdeaFormEvent.OnCostAmountChange -> _state.update {
                it.copy(costAmount = event.value.filter { c -> c.isDigit() || c == '.' || c == ',' })
            }

            is IdeaFormEvent.OnCostTypeChange -> _state.update { it.copy(costType = event.value) }
            is IdeaFormEvent.OnWebsiteChange -> _state.update { it.copy(website = event.value) }
            is IdeaFormEvent.OnNotesChange -> _state.update { it.copy(notes = event.value) }
        }
    }

    private fun loadIdea() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val idea = api.getIdea(ideaId)
                    val trip = api.getTrip(tripId)
                    val itinerary = api.getItinerary(tripId).items
                    val cities =
                        itinerary.mapNotNull { it.city?.takeIf { city -> city.isNotBlank() } }
                            .distinct()
                    LoadedIdea(
                        title = idea.title,
                        city = idea.city.orEmpty(),
                        costAmount = idea.costAmount?.let { formatAmount(it) }.orEmpty(),
                        costType = idea.costType.toIdeaCostType(),
                        website = idea.website.orEmpty(),
                        notes = idea.notes.orEmpty(),
                        currencySymbol = currencySymbolFor(trip.currencyCode),
                        cities = cities,
                    )
                }
            }.onSuccess { loaded ->
                availableCities = loaded.cities
                _state.update {
                    it.copy(
                        title = loaded.title,
                        city = loaded.city,
                        costAmount = loaded.costAmount,
                        costType = loaded.costType,
                        website = loaded.website,
                        notes = loaded.notes,
                        currencySymbol = loaded.currencySymbol,
                    )
                }
            }.onFailure {
                emit(IdeaFormEffect.ShowToastRes(R.string.common_error_message))
            }
        }
    }

    private fun openCityPicker() {
        if (availableCities.isEmpty()) return
        _state.update { it.copy(cityPicker = IdeaCityPickerState(availableCities)) }
    }

    private fun updateIdea() {
        val snapshot = _state.value
        if (snapshot.title.isBlank()) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.updateIdea(
                        ideaId = ideaId,
                        request = UpdateIdeaRequest(
                            title = snapshot.title.trim(),
                            city = snapshot.city.trim().ifBlank { null },
                            costAmount = parseAmount(snapshot.costAmount),
                            costType = snapshot.costAmount.toCostType(snapshot.costType),
                            website = snapshot.website.trim().ifBlank { null },
                            notes = snapshot.notes.trim().ifBlank { null },
                        )
                    )
                }
            }.onSuccess {
                emit(IdeaFormEffect.ShowToastRes(R.string.idea_form_saved_toast))
                appNavigator.popBackStack()
            }.onFailure {
                emit(IdeaFormEffect.ShowToastRes(R.string.common_error_message))
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun deleteIdea() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { api.deleteIdea(ideaId) }
            }.onSuccess {
                emit(IdeaFormEffect.ShowToastRes(R.string.idea_form_deleted_toast))
                appNavigator.popBackStack()
            }.onFailure {
                emit(IdeaFormEffect.ShowToastRes(R.string.common_error_message))
            }
        }
    }

    private fun emit(effect: IdeaFormEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private data class LoadedIdea(
        val title: String,
        val city: String,
        val costAmount: String,
        val costType: IdeaCostType,
        val website: String,
        val notes: String,
        val currencySymbol: String,
        val cities: List<String>,
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
