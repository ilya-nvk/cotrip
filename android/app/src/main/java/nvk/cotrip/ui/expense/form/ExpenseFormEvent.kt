package nvk.cotrip.ui.expense.form

import java.time.LocalDate

sealed interface ExpenseFormEvent {
    data object OnBackClick : ExpenseFormEvent
    data object OnPrimaryClick : ExpenseFormEvent
    data object OnDeleteClick : ExpenseFormEvent
    data object OnDateClick : ExpenseFormEvent
    data class OnDateSelected(val date: LocalDate) : ExpenseFormEvent
    data object OnPaidByClick : ExpenseFormEvent
    data object OnDismissPaidByPicker : ExpenseFormEvent
    data class OnTitleChange(val value: String) : ExpenseFormEvent
    data class OnAmountChange(val value: String) : ExpenseFormEvent
    data class OnStatusChange(val value: ExpenseFormStatus) : ExpenseFormEvent
    data class OnPaidBySelected(val participantId: String) : ExpenseFormEvent
    data class OnParticipantChecked(val participantId: String, val checked: Boolean) :
        ExpenseFormEvent

    data class OnSplitTypeChange(val value: ExpenseSplitType) : ExpenseFormEvent
    data class OnCustomAmountChange(val participantId: String, val value: String) : ExpenseFormEvent
    data class OnNoteChange(val value: String) : ExpenseFormEvent
}
