package nvk.cotrip.ui.expense.list

data class ExpenseSummaryUi(
    val totalSpent: String,
    val balanceLabel: String,
    val balanceAmount: String,
    val totalPlanned: String,
)

data class ExpenseListItemUi(
    val id: String,
    val title: String,
    val amount: String,
    val paidBy: String,
    val splitType: String,
    val settlement: ExpenseSettlementUi,
)

sealed interface ExpenseSettlementUi {
    data class OwedToYou(val amount: String) : ExpenseSettlementUi
    data class YouOwe(val amount: String) : ExpenseSettlementUi
    data object Settled : ExpenseSettlementUi
    data object Planned : ExpenseSettlementUi
}
