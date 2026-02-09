package nvk.cotrip.ui.trip.form

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
import nvk.cotrip.data.network.dto.UpdateTripRequest
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class EditTripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
) : ViewModel() {

    private val tripId: String = checkNotNull(savedStateHandle["tripId"])

    private val _state = MutableStateFlow(TripFormState(isLoading = true))
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripFormEffect>()
    val effects = _effects.asSharedFlow()

    init {
        loadTrip(tripId)
    }

    fun onEvent(event: TripFormEvent) {
        when (event) {
            TripFormEvent.OnCloseClick,
            TripFormEvent.OnCancelClick -> closeScreen()

            TripFormEvent.OnPickCoverClick -> emitToastRes(R.string.trip_form_cover_not_implemented)

            is TripFormEvent.OnNameChange -> {
                _state.update { it.copy(name = event.value) }
                recomputeCanSubmit()
            }

            is TripFormEvent.OnStartDateSelected -> {
                _state.update { it.copy(startDate = event.date) }
                recomputeCanSubmit()
            }

            is TripFormEvent.OnEndDateSelected -> {
                _state.update { it.copy(endDate = event.date) }
                recomputeCanSubmit()
            }

            is TripFormEvent.OnDescriptionChange ->
                _state.update { it.copy(description = event.value) }

            is TripFormEvent.OnCurrencySelect ->
                _state.update { it.copy(currency = event.currency) }

            TripFormEvent.OnPrimaryActionClick -> {
                val s = state.value
                if (!s.canSubmit || s.isLoading) return
                saveTrip()
            }

            TripFormEvent.OnArchiveClick -> {
                if (state.value.isLoading) return
                archiveTrip()
            }

            TripFormEvent.OnDeleteClick -> {
                if (state.value.isLoading) return
                deleteTrip()
            }
        }
    }

    private fun loadTrip(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = runCatching {
                withContext(Dispatchers.IO) { tripRepository.getTrip(id) }
            }
            result.onSuccess { trip ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        coverUri = trip.coverUrl,
                        name = trip.title,
                        startDate = LocalDate.parse(trip.startDate),
                        endDate = LocalDate.parse(trip.endDate),
                        description = trip.description.orEmpty(),
                        currency = trip.currencyCode.toCurrency(),
                    )
                }
                recomputeCanSubmit()
            }.onFailure {
                _state.update { it.copy(isLoading = false) }
                emitToastRes(R.string.common_error_message)
                closeScreen()
            }
        }
    }

    private fun recomputeCanSubmit() {
        _state.update { s ->
            val hasName = s.name.isNotBlank()
            val hasDates = s.startDate != null && s.endDate != null
            val datesOk = if (hasDates) !s.endDate!!.isBefore(s.startDate) else false
            s.copy(canSubmit = hasName && hasDates && datesOk)
        }
    }

    private fun emitToastRes(resId: Int) {
        viewModelScope.launch { _effects.emit(TripFormEffect.ShowToastRes(resId)) }
    }

    private fun closeScreen() {
        appNavigator.popBackStack()
    }

    private fun saveTrip() {
        viewModelScope.launch {
            val s = state.value
            val startDate = s.startDate ?: return@launch
            val endDate = s.endDate ?: return@launch
            _state.update { it.copy(isLoading = true) }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    tripRepository.updateTrip(
                        tripId = tripId,
                        request = UpdateTripRequest(
                            title = s.name,
                            description = s.description.takeIf { it.isNotBlank() },
                            startDate = startDate.toString(),
                            endDate = endDate.toString(),
                            locationLine = null,
                            coverUrl = s.coverUri,
                            currencyCode = s.currency.code,
                        )
                    )
                }
            }

            result.onSuccess {
                emitToastRes(R.string.edit_trip_saved_toast)
                appNavigator.navigate(Destination.OutOfRangeDays(tripId))
            }.onFailure {
                emitToastRes(R.string.common_error_message)
            }

            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun archiveTrip() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = runCatching {
                withContext(Dispatchers.IO) { tripRepository.archiveTrip(tripId) }
            }
            result.onSuccess {
                emitToastRes(R.string.edit_trip_archived_toast)
                closeScreen()
            }.onFailure {
                emitToastRes(R.string.common_error_message)
            }
            _state.update { it.copy(isLoading = false) }
        }
    }

    private fun deleteTrip() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = runCatching {
                withContext(Dispatchers.IO) { tripRepository.deleteTrip(tripId) }
            }
            result.onSuccess {
                emitToastRes(R.string.edit_trip_deleted_toast)
                closeScreen()
            }.onFailure {
                emitToastRes(R.string.common_error_message)
            }
            _state.update { it.copy(isLoading = false) }
        }
    }
}

private fun String.toCurrency(): TripCurrency {
    return TripCurrency.entries.firstOrNull { it.code == this } ?: TripCurrency.EUR
}
