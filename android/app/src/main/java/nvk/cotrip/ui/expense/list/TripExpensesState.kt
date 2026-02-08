package nvk.cotrip.ui.expense.list

data class TripExpensesState(
    val tripId: String,
    val summary: ExpenseSummaryUi,
    val spent: List<ExpenseListItemUi>,
    val planned: List<ExpenseListItemUi>,
)
