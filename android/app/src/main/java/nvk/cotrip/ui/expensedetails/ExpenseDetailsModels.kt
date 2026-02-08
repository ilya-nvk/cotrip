package nvk.cotrip.ui.expensedetails

enum class ExpenseDetailsStatus {
    Planned,
    Unsettled,
    Settled,
}

data class ExpenseSplitRowUi(
    val id: String,
    val initials: String,
    val name: String,
    val amount: String,
    val isPaid: Boolean,
)
