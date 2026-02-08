package nvk.cotrip.ui.expenseform

sealed interface ExpenseFormEffect {
    data class ShowToastRes(val resId: Int) : ExpenseFormEffect
}
