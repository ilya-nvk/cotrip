package nvk.cotrip.ui.expenses

sealed interface TripExpensesEvent {
    data object OnBackClick : TripExpensesEvent
    data object OnAddExpenseClick : TripExpensesEvent
    data class OnExpenseClick(val expenseId: String) : TripExpensesEvent
}
