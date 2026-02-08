package nvk.cotrip.ui.activity.form

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
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import nvk.cotrip.ui.trip.form.TripCurrency
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CreateActivityViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val api: CoTripApi,
) : ViewModel(), ActivityFormContract {

    private val tripId: String =
        checkNotNull(savedStateHandle.get<String>(Destination.CreateActivity.ARG_TRIP_ID))

    private var dayByDate: Map<LocalDate, ItineraryDayDto> = emptyMap()
    private var selectedDayId: String? = null

    private val _state = MutableStateFlow(
        ActivityFormState(
            mode = ActivityFormMode.Create,
            activityId = null,
            headerText = null,
            title = "",
            dateText = "",
            timeText = "",
            locationName = "",
            locationLink = "",
            currencySymbol = "€",
            costAmount = "",
            costType = CostType.PerPerson,
            website = "",
            notes = "",
            isSaving = false
        )
    )
    override val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ActivityFormEffect>()
    override val effects = _effects.asSharedFlow()

    init {
        loadTripMeta()
    }

    override fun onEvent(event: ActivityFormEvent) {
        when (event) {
            ActivityFormEvent.OnBackClick -> appNavigator.popBackStack()
            ActivityFormEvent.OnPrimaryClick -> createActivity()
            ActivityFormEvent.OnDeleteClick -> emit(ActivityFormEffect.ShowToastRes(R.string.activity_form_delete_not_available))
            ActivityFormEvent.OnPickDateClick -> Unit
            ActivityFormEvent.OnPickTimeClick -> Unit
            is ActivityFormEvent.OnDateSelected -> selectDate(event.date)
            is ActivityFormEvent.OnTimeSelected -> selectTime(event.time)
            is ActivityFormEvent.OnTitleChange -> _state.update { it.copy(title = event.value) }
            is ActivityFormEvent.OnLocationNameChange -> _state.update { it.copy(locationName = event.value) }
            is ActivityFormEvent.OnLocationLinkChange -> _state.update { it.copy(locationLink = event.value) }
            is ActivityFormEvent.OnCostAmountChange -> _state.update {
                it.copy(
                    costAmount = moneyInput(
                        event.value
                    )
                )
            }
            is ActivityFormEvent.OnCostTypeChange -> _state.update { it.copy(costType = event.value) }
            is ActivityFormEvent.OnWebsiteChange -> _state.update { it.copy(website = event.value) }
            is ActivityFormEvent.OnNotesChange -> _state.update { it.copy(notes = event.value) }
        }
    }

    private fun loadTripMeta() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val trip = api.getTrip(tripId)
                    val itinerary = api.getItinerary(tripId).items.sortedBy { it.dayNumber }
                    val firstDay = itinerary.firstOrNull()
                    val dayMap = itinerary.associateBy { LocalDate.parse(it.date) }
                    TripMeta(
                        currencySymbol = currencySymbolFor(trip.currencyCode),
                        firstDay = firstDay,
                        dayByDate = dayMap
                    )
                }
            }.onSuccess { meta ->
                dayByDate = meta.dayByDate
                selectedDayId = meta.firstDay?.id
                _state.update {
                    it.copy(
                        currencySymbol = meta.currencySymbol,
                        dateText = meta.firstDay?.let { day -> formatDate(LocalDate.parse(day.date)) }
                            .orEmpty(),
                        headerText = meta.firstDay?.let { day -> headerFor(day) }
                    )
                }
            }.onFailure {
                emit(ActivityFormEffect.ShowToastRes(R.string.common_error_message))
            }
        }
    }

    private fun selectDate(date: LocalDate) {
        val day = dayByDate[date] ?: run {
            emit(ActivityFormEffect.ShowToastRes(R.string.common_error_message))
            return
        }
        selectedDayId = day.id
        _state.update {
            it.copy(
                dateText = formatDate(date),
                headerText = headerFor(day)
            )
        }
    }

    private fun selectTime(time: LocalTime) {
        _state.update { it.copy(timeText = time.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    }

    private fun createActivity() {
        val snapshot = _state.value
        val dayId = selectedDayId
        if (dayId.isNullOrBlank() || snapshot.title.isBlank()) {
            emit(ActivityFormEffect.ShowToastRes(R.string.common_error_message))
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.createActivity(
                        dayId = dayId,
                        request = CreateActivityRequest(
                            title = snapshot.title.trim(),
                            timeText = snapshot.timeText.trim().ifBlank { null },
                            locationName = snapshot.locationName.trim().ifBlank { null },
                            locationLink = snapshot.locationLink.trim().ifBlank { null },
                            costAmount = parseAmount(snapshot.costAmount),
                            costType = snapshot.costAmount.toCostType(snapshot.costType),
                            website = snapshot.website.trim().ifBlank { null },
                            notes = snapshot.notes.trim().ifBlank { null },
                        )
                    )
                }
            }.onSuccess {
                emit(ActivityFormEffect.ShowToastRes(R.string.activity_form_created_toast))
                appNavigator.popBackStack()
            }.onFailure {
                emit(ActivityFormEffect.ShowToastRes(R.string.common_error_message))
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun moneyInput(value: String): String {
        return value.filter { it.isDigit() || it == '.' || it == ',' }
    }

    private fun emit(effect: ActivityFormEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private data class TripMeta(
        val currencySymbol: String,
        val firstDay: ItineraryDayDto?,
        val dayByDate: Map<LocalDate, ItineraryDayDto>,
    )
}

private fun formatDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault()))
}

private fun headerFor(day: ItineraryDayDto): String {
    return if (day.city.isNullOrBlank()) {
        "Day ${day.dayNumber}"
    } else {
        "Day ${day.dayNumber} · ${day.city}"
    }
}

private fun parseAmount(amount: String): Double? {
    val normalized = amount.replace(',', '.').trim()
    return normalized.toDoubleOrNull()
}

private fun String.toCostType(type: CostType): String? {
    if (this.replace(',', '.').trim().isBlank()) return null
    return when (type) {
        CostType.PerPerson -> "per_person"
        CostType.Total -> "total"
    }
}

private fun currencySymbolFor(code: String): String {
    return TripCurrency.values().firstOrNull { it.code == code }?.symbol ?: code
}
