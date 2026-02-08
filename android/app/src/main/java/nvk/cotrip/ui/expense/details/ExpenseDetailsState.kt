package nvk.cotrip.ui.expense.details

data class ExpenseDetailsState(
    val tripId: String,
    val expenseId: String,
    val title: String,
    val amount: String,
    val status: ExpenseDetailsStatus,
    val paidBy: String?,
    val date: String?,
    val splitType: String,
    val note: String?,
    val splitRows: List<ExpenseSplitRowUi>,
    val total: String,
)
