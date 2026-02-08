package nvk.cotrip.ui.expense.form

sealed interface ExpenseFormEffect {
    data class ShowToastRes(val resId: Int) : ExpenseFormEffect
}
