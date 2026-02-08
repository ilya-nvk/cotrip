package nvk.cotrip.ui.idea.form

sealed interface IdeaFormEffect {
    data class ShowToastRes(val resId: Int) : IdeaFormEffect
}
