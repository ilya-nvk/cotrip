package nvk.cotrip.ui.outofrangedays

sealed interface OutOfRangeDaysEffect {
    data class ShowToastRes(val resId: Int) : OutOfRangeDaysEffect
}
