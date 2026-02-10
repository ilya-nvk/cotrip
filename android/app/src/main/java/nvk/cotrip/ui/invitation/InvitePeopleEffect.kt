package nvk.cotrip.ui.invitation

sealed interface InvitePeopleEffect {
    data class ShowToastRes(val resId: Int) : InvitePeopleEffect
    data class CopyToClipboard(val text: String) : InvitePeopleEffect
    data class ShareText(val text: String) : InvitePeopleEffect
}
