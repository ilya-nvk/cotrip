package nvk.cotrip.ui.expense.list

sealed interface TripExpensesState {
    data object Loading : TripExpensesState

    data class Content(
        val tripId: String,
        val summary: ExpenseSummaryUi,
        val spent: List<ExpenseListItemUi>,
        val planned: List<ExpenseListItemUi>,
        val isRefreshing: Boolean = false,
    ) : TripExpensesState
}
