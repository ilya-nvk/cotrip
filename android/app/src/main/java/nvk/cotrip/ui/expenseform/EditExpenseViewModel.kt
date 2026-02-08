package nvk.cotrip.ui.expenseform

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
import javax.inject.Inject

@HiltViewModel
class EditExpenseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel(), ExpenseFormContract {

    private val tripId: String = checkNotNull(savedStateHandle[Destination.EditExpense.ARG_TRIP_ID])
    private val expenseId: String =
        checkNotNull(savedStateHandle[Destination.EditExpense.ARG_EXPENSE_ID])

    private val _state = MutableStateFlow(
        ExpenseFormState(
            mode = ExpenseFormMode.Edit,
            expenseId = expenseId,
            title = "Louvre Museum tickets",
            amount = "68",
            currencySymbol = "€",
            status = ExpenseFormStatus.Paid,
            paidById = "u1",
            dateText = "Jul 16, 2026",
            participants = defaultExpenseParticipants.map {
                when (it.id) {
                    "u1" -> it.copy(customAmount = "17")
                    "u2" -> it.copy(customAmount = "17")
                    "u3" -> it.copy(customAmount = "17")
                    "u4" -> it.copy(customAmount = "17")
                    else -> it
                }
            },
            splitType = ExpenseSplitType.SplitEqually,
            note = "Bought online in advance",
            isSaving = false,
            paidByPickerVisible = false
        )
    )
    override val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ExpenseFormEffect>()
    override val effects = _effects.asSharedFlow()

    override fun onEvent(event: ExpenseFormEvent) {
        when (event) {
            ExpenseFormEvent.OnBackClick -> appNavigator.popBackStack()
            ExpenseFormEvent.OnPrimaryClick -> {
                emit(ExpenseFormEffect.ShowToastRes(R.string.expense_form_saved_toast))
                appNavigator.popBackStack()
            }

            ExpenseFormEvent.OnDeleteClick -> {
                emit(ExpenseFormEffect.ShowToastRes(R.string.expense_form_deleted_toast))
                appNavigator.popBackStack()
            }

            ExpenseFormEvent.OnDateClick -> emit(ExpenseFormEffect.ShowToastRes(R.string.expense_form_date_not_implemented))
            ExpenseFormEvent.OnPaidByClick -> _state.update { it.copy(paidByPickerVisible = true) }
            ExpenseFormEvent.OnDismissPaidByPicker -> _state.update { it.copy(paidByPickerVisible = false) }
            is ExpenseFormEvent.OnTitleChange -> _state.update { it.copy(title = event.value) }
            is ExpenseFormEvent.OnAmountChange -> _state.update { it.copy(amount = moneyInput(event.value)) }
            is ExpenseFormEvent.OnStatusChange -> _state.update {
                it.copy(
                    status = event.value,
                    paidById = if (event.value == ExpenseFormStatus.Planned) null else it.paidById
                )
            }

            is ExpenseFormEvent.OnPaidBySelected -> _state.update {
                it.copy(
                    paidById = event.participantId,
                    paidByPickerVisible = false
                )
            }

            is ExpenseFormEvent.OnParticipantChecked -> _state.update { current ->
                current.copy(
                    participants = current.participants.map { participant ->
                        if (participant.id == event.participantId) participant.copy(isSelected = event.checked)
                        else participant
                    }
                )
            }

            is ExpenseFormEvent.OnSplitTypeChange -> _state.update { it.copy(splitType = event.value) }
            is ExpenseFormEvent.OnCustomAmountChange -> _state.update { current ->
                current.copy(
                    participants = current.participants.map { participant ->
                        if (participant.id == event.participantId) participant.copy(
                            customAmount = moneyInput(
                                event.value
                            )
                        )
                        else participant
                    }
                )
            }

            is ExpenseFormEvent.OnNoteChange -> _state.update { it.copy(note = event.value) }
        }
    }

    private fun moneyInput(value: String): String {
        return value.filter { it.isDigit() || it == '.' || it == ',' }
    }

    private fun emit(effect: ExpenseFormEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
