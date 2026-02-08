package nvk.cotrip.ui.expense.details

sealed interface ExpenseDetailsEvent {
    data object OnBackClick : ExpenseDetailsEvent
    data object OnEditClick : ExpenseDetailsEvent
    data object OnMarkAsPaidClick : ExpenseDetailsEvent
    data object OnMarkAllSettledClick : ExpenseDetailsEvent
    data class OnMarkParticipantPaidClick(val participantId: String) : ExpenseDetailsEvent
}
