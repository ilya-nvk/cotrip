package nvk.cotrip.ui.expense.list

sealed interface TripExpensesEffect {
    data class ShowToastRes(val resId: Int) : TripExpensesEffect
}
