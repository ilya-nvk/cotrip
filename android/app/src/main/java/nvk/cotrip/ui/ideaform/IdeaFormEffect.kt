package nvk.cotrip.ui.ideaform

sealed interface IdeaFormEffect {
    data class ShowToastRes(val resId: Int) : IdeaFormEffect
}
