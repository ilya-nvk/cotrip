package nvk.cotrip.ui.expense.form

enum class ExpenseFormMode {
    Create,
    Edit,
}

enum class ExpenseFormStatus {
    Planned,
    Paid,
}

enum class ExpenseSplitType {
    SplitEqually,
    CustomAmounts,
}

data class ExpenseParticipantUi(
    val id: String,
    val initials: String,
    val name: String,
    val isSelected: Boolean,
    val customAmount: String,
)
