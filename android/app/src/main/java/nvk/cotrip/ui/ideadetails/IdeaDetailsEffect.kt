package nvk.cotrip.ui.ideadetails

sealed interface IdeaDetailsEffect {
    data class ShowToastRes(val resId: Int) : IdeaDetailsEffect
}
