package nvk.cotrip.ui.expense.details

enum class ExpenseDetailsStatus {
    Planned,
    Unsettled,
    Settled,
}

data class ExpenseSplitRowUi(
    val id: String,
    val initials: String,
    val name: String,
    val photoUrl: String?,
    val amount: String,
    val isPaid: Boolean,
    val canTogglePaid: Boolean,
)
