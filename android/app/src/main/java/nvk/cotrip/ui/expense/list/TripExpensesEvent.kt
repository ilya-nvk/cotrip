package nvk.cotrip.ui.expense.list

sealed interface TripExpensesEvent {
    data object OnBackClick : TripExpensesEvent
    data object OnAutoRefresh : TripExpensesEvent
    data object OnUserRefresh : TripExpensesEvent
    data object OnAddExpenseClick : TripExpensesEvent
    data class OnExpenseClick(val expenseId: String) : TripExpensesEvent
}
