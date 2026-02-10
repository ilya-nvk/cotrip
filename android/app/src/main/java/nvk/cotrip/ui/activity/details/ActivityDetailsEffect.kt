package nvk.cotrip.ui.activity.details

sealed interface ActivityDetailsEffect {
    data class ShowToastRes(val resId: Int) : ActivityDetailsEffect
    data class OpenExternalLink(val url: String) : ActivityDetailsEffect
}
