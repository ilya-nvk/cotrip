package nvk.cotrip.ui.activity.form

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
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MoveActivityRequest
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UpdateActivityRequest
import nvk.cotrip.data.repository.ItineraryRepository
import nvk.cotrip.data.repository.TripRepository
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
class EditActivityViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val itineraryRepository: ItineraryRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel(), ActivityFormContract {

    private val activityId: String =
        checkNotNull(savedStateHandle.get<String>(Destination.EditActivity.ARG_ACTIVITY_ID))

    private var selectedDayId: String? = null
    private var originalDayId: String? = null
    private var dayByDate: Map<LocalDate, ItineraryDayDto> = emptyMap()
    private var tripId: String? = null
    private var locationSearchJob: Job? = null

    private val _state = MutableStateFlow(
        ActivityFormState(
            mode = ActivityFormMode.Edit,
            activityId = activityId,
            headerText = null,
            title = "",
            dateText = "",
            timeText = "",
            locationInput = "",
            locationPlaceId = null,
            locationSuggestions = emptyList(),
            isLocationSearching = false,
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
        loadActivity()
    }

    override fun onEvent(event: ActivityFormEvent) {
        when (event) {
            ActivityFormEvent.OnBackClick -> appNavigator.popBackStack()
            ActivityFormEvent.OnPrimaryClick -> updateActivity()
            ActivityFormEvent.OnDeleteClick -> deleteActivity()
            ActivityFormEvent.OnPickDateClick -> Unit
            ActivityFormEvent.OnPickTimeClick -> Unit
            is ActivityFormEvent.OnDateSelected -> selectDate(event.date)
            is ActivityFormEvent.OnTimeSelected -> selectTime(event.time)
            is ActivityFormEvent.OnTitleChange -> _state.update { it.copy(title = event.value) }
            is ActivityFormEvent.OnLocationInputChange -> onLocationInputChanged(event.value)
            is ActivityFormEvent.OnLocationSuggestionSelected -> onLocationSuggestionSelected(event.value)
            is ActivityFormEvent.OnCostAmountChange -> _state.update { it.copy(costAmount = moneyInput(event.value)) }
            is ActivityFormEvent.OnCostTypeChange -> _state.update { it.copy(costType = event.value) }
            is ActivityFormEvent.OnWebsiteChange -> _state.update { it.copy(website = event.value) }
            is ActivityFormEvent.OnNotesChange -> _state.update { it.copy(notes = event.value) }
        }
    }

    private fun loadActivity() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    findActivity(activityId)
                }
            }) {
                is ApiResult.Success -> {
                    val info = result.data
                    tripId = info.trip.id
                    selectedDayId = info.day.id
                    originalDayId = info.day.id
                    dayByDate = info.days.associateBy { LocalDate.parse(it.date) }
                    _state.update {
                        it.copy(
                            headerText = headerFor(info.day),
                            title = info.activity.title,
                            dateText = formatDate(LocalDate.parse(info.day.date)),
                            timeText = info.activity.timeText.orEmpty(),
                            locationInput = info.activity.locationName.orEmpty(),
                            locationPlaceId = extractGooglePlaceId(info.activity.locationLink),
                            locationSuggestions = emptyList(),
                            isLocationSearching = false,
                            costAmount = info.activity.costAmount?.let { amount ->
                                formatAmount(
                                    amount
                                )
                            }
                                .orEmpty(),
                            costType = info.activity.costType.toCostType(),
                            website = info.activity.website.orEmpty(),
                            notes = info.activity.notes.orEmpty(),
                            currencySymbol = currencySymbolFor(info.trip.currencyCode)
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

    private fun selectTime(time: LocalTime) {
        _state.update { it.copy(timeText = time.format(DateTimeFormatter.ofPattern("HH:mm"))) }
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

    private fun updateActivity() {
        val snapshot = _state.value
        if (snapshot.title.isBlank()) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    val targetDayId = selectedDayId
                    val original = originalDayId
                    if (!targetDayId.isNullOrBlank() && targetDayId != original) {
                        itineraryRepository.moveActivity(
                            activityId = activityId,
                            request = MoveActivityRequest(dayId = targetDayId)
                        )
                    }
                    itineraryRepository.updateActivity(
                        activityId = activityId,
                        request = UpdateActivityRequest(
                            title = snapshot.title.trim(),
                            timeText = snapshot.timeText.trim().ifBlank { null },
                            locationName = snapshot.locationInput.trim().ifBlank { null },
                            locationLink = snapshot.locationPlaceId?.toGoogleMapsPlaceLink(),
                            costAmount = parseAmount(snapshot.costAmount),
                            costType = snapshot.costAmount.toCostType(snapshot.costType),
                            website = snapshot.website.trim().ifBlank { null },
                            notes = snapshot.notes.trim().ifBlank { null },
                        )
                    )
                }
            }) {
                is ApiResult.Success -> {
                    emit(ActivityFormEffect.ShowToastRes(R.string.activity_form_saved_toast))
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
        val query = value.trim()
        locationSearchJob?.cancel()
        _state.update {
            it.copy(
                locationInput = value,
                locationPlaceId = null,
                locationSuggestions = emptyList(),
                isLocationSearching = query.isNotBlank(),
            )
        }

        val trip = tripId ?: return
        if (query.isBlank()) {
            return
        }

        locationSearchJob = viewModelScope.launch {
            delay(300)
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    itineraryRepository.searchPlaces(tripId = trip, query = query, limit = 8)
                }
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
                locationInput = value.fullText,
                locationPlaceId = value.placeId,
                locationSuggestions = emptyList(),
                isLocationSearching = false,
            )
        }
    }

    private fun deleteActivity() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) { itineraryRepository.deleteActivity(activityId) }
            }) {
                is ApiResult.Success -> {
                    emit(ActivityFormEffect.ShowToastRes(R.string.activity_form_deleted_toast))
                    appNavigator.popBackStack()
                }

                is ApiResult.Failure -> {
                    emit(ActivityFormEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun moneyInput(value: String): String {
        return value.filter { it.isDigit() || it == '.' || it == ',' }
    }

    private suspend fun findActivity(activityId: String): ActivityLookup {
        val trips = tripRepository.listTrips()
        trips.forEach { trip ->
            val itinerary = itineraryRepository.getItinerary(trip.id)
            itinerary.forEach { day ->
                val match = day.activities.firstOrNull { it.id == activityId }
                if (match != null) {
                    return ActivityLookup(trip, day, match, itinerary)
                }
            }
        }
        throw IllegalStateException("Activity not found")
    }

    private fun emit(effect: ActivityFormEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private data class ActivityLookup(
        val trip: TripDto,
        val day: ItineraryDayDto,
        val activity: ActivityDto,
        val days: List<ItineraryDayDto>,
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

private fun String?.toCostType(): CostType {
    return when (this) {
        "total" -> CostType.Total
        else -> CostType.PerPerson
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

private fun formatAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        amount.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.2f", amount)
    }
}
