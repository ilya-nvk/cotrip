package nvk.cotrip.ui.idea.list

sealed interface TripIdeasEffect {
    data class ShowToastRes(val resId: Int) : TripIdeasEffect
}
