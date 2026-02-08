package nvk.cotrip.ui.trip.form

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
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class EditTripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
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

            TripFormEvent.OnStartDateClick -> emitToastRes(R.string.trip_form_date_not_implemented)
            TripFormEvent.OnEndDateClick -> emitToastRes(R.string.trip_form_date_not_implemented)

            is TripFormEvent.OnDescriptionChange ->
                _state.update { it.copy(description = event.value) }

            is TripFormEvent.OnCurrencySelect ->
                _state.update { it.copy(currency = event.currency) }

            TripFormEvent.OnPrimaryActionClick -> {
                val s = state.value
                if (!s.canSubmit || s.isLoading) return
                emitToastRes(R.string.edit_trip_saved_toast)
                appNavigator.navigate(Destination.OutOfRangeDays(tripId))
            }

            TripFormEvent.OnArchiveClick -> {
                if (state.value.isLoading) return
                emitToastRes(R.string.edit_trip_archived_toast)
                closeScreen()
            }

            TripFormEvent.OnDeleteClick -> {
                if (state.value.isLoading) return
                emitToastRes(R.string.edit_trip_deleted_toast)
                closeScreen()
            }
        }
    }

    private fun loadTrip(id: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = false,
                    coverUri = null,
                    name = "Summer Europe Trip",
                    startDate = LocalDate.of(2026, 7, 15),
                    endDate = LocalDate.of(2026, 7, 29),
                    description = "Exploring the beautiful cities of Europe with friends",
                    currency = TripCurrency.EUR,
                )
            }
            recomputeCanSubmit()
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
}
