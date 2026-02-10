package nvk.cotrip.ui.expense.details

sealed interface ExpenseDetailsEffect {
    data class ShowToastRes(val resId: Int) : ExpenseDetailsEffect
}
