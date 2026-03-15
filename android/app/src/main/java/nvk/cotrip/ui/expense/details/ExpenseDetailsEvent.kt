package nvk.cotrip.ui.expense.details

sealed interface ExpenseDetailsEvent {
    data object OnBackClick : ExpenseDetailsEvent
    data object OnRefresh : ExpenseDetailsEvent
    data object OnEditClick : ExpenseDetailsEvent
    data object OnMarkAsPaidClick : ExpenseDetailsEvent
    data class OnMarkParticipantPaidClick(val participantId: String) : ExpenseDetailsEvent
    data class OnUnmarkParticipantPaidClick(val participantId: String) : ExpenseDetailsEvent
}
