package nvk.cotrip.ui.activity.form

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.limitReachedDetails
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.LimitDialogState
import nvk.cotrip.ui.common.TextInputLimits
import nvk.cotrip.ui.common.UiErrorMapper
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
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel(), ActivityFormContract {

    private val tripId: String =
        checkNotNull(savedStateHandle.get<String>(Destination.CreateActivity.ARG_TRIP_ID))

    private var dayByDate: Map<LocalDate, ItineraryDayDto> = emptyMap()
    private var selectedDayId: String? = null
    private var locationSearchJob: Job? = null

    private val _state = MutableStateFlow(
        ActivityFormState(
            mode = ActivityFormMode.Create,
            activityId = null,
            headerDayNumber = null,
            headerCity = null,
            tripStartDate = null,
            tripEndDate = null,
            title = "",
            dateText = "",
            timeText = "",
            locationInput = "",
            locationPlaceId = null,
            linkInput = "",
            locationSuggestions = emptyList(),
            isLocationSearching = false,
            currencySymbol = "€",
            costAmount = "",
            costType = CostType.PerPerson,
            notes = "",
            isSaving = false
        )
    )
    override val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ActivityFormEffect>(extraBufferCapacity = 8)
    override val effects = _effects.asSharedFlow()

    init {
        loadTripMeta()
    }

    override fun onEvent(event: ActivityFormEvent) {
        when (event) {
            ActivityFormEvent.OnBackClick -> appNavigator.popBackStack()
            ActivityFormEvent.OnPrimaryClick -> createActivity()
            ActivityFormEvent.OnDeleteClick -> emit(ActivityFormEffect.ShowToastRes(R.string.activity_form_delete_not_available))
            ActivityFormEvent.OnDismissLimitDialog -> _state.update { it.copy(limitDialog = null) }
            ActivityFormEvent.OnConfirmDeleteOldestAndRetry -> deleteOldestAndRetry()
            ActivityFormEvent.OnPickDateClick -> Unit
            ActivityFormEvent.OnPickTimeClick -> Unit
            is ActivityFormEvent.OnDateSelected -> selectDate(event.date)
            is ActivityFormEvent.OnTimeSelected -> selectTime(event.time)
            is ActivityFormEvent.OnTitleChange -> _state.update {
                it.copy(title = event.value.take(TextInputLimits.ACTIVITY_TITLE))
            }
            is ActivityFormEvent.OnLocationInputChange -> onLocationInputChanged(event.value)
            is ActivityFormEvent.OnLocationSuggestionSelected -> onLocationSuggestionSelected(event.value)
            is ActivityFormEvent.OnLinkChange -> _state.update {
                it.copy(linkInput = event.value.take(TextInputLimits.ACTIVITY_LINK))
            }
            is ActivityFormEvent.OnCostAmountChange -> _state.update {
                it.copy(
                    costAmount = moneyInput(
                        event.value
                    )
                )
            }
            is ActivityFormEvent.OnCostTypeChange -> _state.update { it.copy(costType = event.value) }
            is ActivityFormEvent.OnNotesChange -> _state.update {
                it.copy(notes = event.value.take(TextInputLimits.ACTIVITY_NOTES))
            }
        }
    }

    private fun loadTripMeta() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                val trip = tripRepository.getTrip(tripId).first()
                val itinerary = itineraryRepository.getItinerary(tripId).first()
                    .sortedBy { it.dayNumber }
                val availableDays = itinerary.filter { !it.isOutOfRange }
                val firstDay = availableDays.firstOrNull()
                val dayMap = availableDays.associateBy { LocalDate.parse(it.date) }
                TripMeta(
                    currencySymbol = currencySymbolFor(trip.currencyCode),
                    startDate = LocalDate.parse(trip.startDate),
                    endDate = LocalDate.parse(trip.endDate),
                    firstDay = firstDay,
                    dayByDate = dayMap
                )
            }) {
                is ApiResult.Success -> {
                    val meta = result.data
                    dayByDate = meta.dayByDate
                    selectedDayId = meta.firstDay?.id
                    _state.update {
                        it.copy(
                            currencySymbol = meta.currencySymbol,
                            tripStartDate = meta.startDate,
                            tripEndDate = meta.endDate,
                            dateText = meta.firstDay?.let { day -> formatDate(LocalDate.parse(day.date)) }
                                .orEmpty(),
                            headerDayNumber = meta.firstDay?.dayNumber,
                            headerCity = meta.firstDay?.city?.takeIf { city -> city.isNotBlank() }
                        )
                    }
                }

                is ApiResult.Failure -> emit(
                    ActivityFormEffect.ShowToastRes(
                        uiErrorMapper.messageRes(result)
                    )
                )
            }
        }
    }

    private fun selectDate(date: LocalDate) {
        val day = dayByDate[date] ?: run {
            emit(ActivityFormEffect.ShowToastRes(R.string.activity_form_date_out_of_trip_range))
            return
        }
        selectedDayId = day.id
        _state.update {
            it.copy(
                dateText = formatDate(date),
                headerDayNumber = day.dayNumber,
                headerCity = day.city?.takeIf { city -> city.isNotBlank() }
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
            when (val result = apiCaller.call {
                itineraryRepository.createActivity(
                    dayId = dayId,
                    request = CreateActivityRequest(
                        title = snapshot.title.trim(),
                        timeText = snapshot.timeText.trim().ifBlank { null },
                        locationName = snapshot.locationInput.trim().ifBlank { null },
                        link = snapshot.linkInput.trim().ifBlank { null },
                        costAmount = parseAmount(snapshot.costAmount),
                        costType = snapshot.costAmount.toCostType(snapshot.costType),
                        notes = snapshot.notes.trim().ifBlank { null },
                    )
                )
            }) {
                is ApiResult.Success -> {
                    emit(ActivityFormEffect.ShowToastRes(R.string.activity_form_created_toast))
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> {
                    val limit = result.limitReachedDetails()
                    val oldest = limit?.oldestCandidate
                    if (oldest?.deletable == true) {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                limitDialog = LimitDialogState(
                                    oldestId = oldest.id,
                                    oldestLabel = oldest.label,
                                )
                            )
                        }
                    } else {
                        emit(ActivityFormEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                        _state.update { it.copy(isSaving = false) }
                    }
                }
            }
        }
    }

    private fun deleteOldestAndRetry() {
        val dialog = _state.value.limitDialog ?: return
        val snapshot = _state.value
        val dayId = selectedDayId ?: return
        _state.update { it.copy(limitDialog = null, isSaving = true) }
        viewModelScope.launch {
            when (val result = apiCaller.call {
                itineraryRepository.deleteActivity(dialog.oldestId)
                itineraryRepository.createActivity(
                    dayId = dayId,
                    request = CreateActivityRequest(
                        title = snapshot.title.trim(),
                        timeText = snapshot.timeText.trim().ifBlank { null },
                        locationName = snapshot.locationInput.trim().ifBlank { null },
                        link = snapshot.linkInput.trim().ifBlank { null },
                        costAmount = parseAmount(snapshot.costAmount),
                        costType = snapshot.costAmount.toCostType(snapshot.costType),
                        notes = snapshot.notes.trim().ifBlank { null },
                    )
                )
            }) {
                is ApiResult.Success -> {
                    emit(ActivityFormEffect.ShowToastRes(R.string.activity_form_created_toast))
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> {
                    emit(ActivityFormEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                    _state.update { it.copy(isSaving = false) }
                }
            }
        }
    }

    private fun onLocationInputChanged(value: String) {
        val limitedValue = value.take(TextInputLimits.ACTIVITY_LOCATION)
        val query = limitedValue.trim()
        locationSearchJob?.cancel()
        _state.update {
            it.copy(
                locationInput = limitedValue,
                locationPlaceId = null,
                locationSuggestions = emptyList(),
                isLocationSearching = query.isNotBlank(),
            )
        }

        if (query.isBlank()) {
            return
        }

        locationSearchJob = viewModelScope.launch {
            delay(300)
            when (val result = apiCaller.call {
                itineraryRepository.searchPlaces(tripId = tripId, query = query, limit = 8)
            }) {
                is ApiResult.Success -> {
                    val mapped = result.data.map {
                        LocationSuggestionUi(
                            name = it.name,
                            placeId = it.placeId,
                            fullText = it.fullText,
                        )
                    }
                    _state.update {
                        if (it.locationInput.trim() != query) return@update it
                        it.copy(locationSuggestions = mapped, isLocationSearching = false)
                    }
                }

                is ApiResult.Failure -> {
                    _state.update {
                        if (it.locationInput.trim() != query) return@update it
                        it.copy(locationSuggestions = emptyList(), isLocationSearching = false)
                    }
                }
            }
        }
    }

    private fun onLocationSuggestionSelected(value: LocationSuggestionUi) {
        locationSearchJob?.cancel()
        _state.update {
            it.copy(
                locationInput = value.fullText.take(TextInputLimits.ACTIVITY_LOCATION),
                locationPlaceId = value.placeId,
                locationSuggestions = emptyList(),
                isLocationSearching = false,
            )
        }
    }

    private fun moneyInput(value: String): String {
        return value
            .filter { it.isDigit() || it == '.' || it == ',' }
            .take(TextInputLimits.ACTIVITY_COST_AMOUNT)
    }

    private fun emit(effect: ActivityFormEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private data class TripMeta(
        val currencySymbol: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val firstDay: ItineraryDayDto?,
        val dayByDate: Map<LocalDate, ItineraryDayDto>,
    )
}

private fun formatDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault()))
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
