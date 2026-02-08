package nvk.cotrip.ui.idea.details

sealed interface IdeaDetailsEffect {
    data class ShowToastRes(val resId: Int) : IdeaDetailsEffect
}
