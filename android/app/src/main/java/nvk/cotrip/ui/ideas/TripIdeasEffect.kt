package nvk.cotrip.ui.ideas

sealed interface TripIdeasEffect {
    data class ShowToastRes(val resId: Int) : TripIdeasEffect
}
