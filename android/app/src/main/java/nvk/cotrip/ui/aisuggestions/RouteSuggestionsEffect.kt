package nvk.cotrip.ui.aisuggestions

sealed interface RouteSuggestionsEffect {
    data class ShowToastRes(val resId: Int) : RouteSuggestionsEffect
}
