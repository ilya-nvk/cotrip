package nvk.cotrip.ui.expenseform

data class ExpenseFormState(
    val mode: ExpenseFormMode,
    val expenseId: String?,
    val title: String,
    val amount: String,
    val currencySymbol: String,
    val status: ExpenseFormStatus,
    val paidById: String?,
    val dateText: String,
    val participants: List<ExpenseParticipantUi>,
    val splitType: ExpenseSplitType,
    val note: String,
    val isSaving: Boolean,
    val paidByPickerVisible: Boolean,
)
