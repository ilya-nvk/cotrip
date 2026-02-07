package nvk.cotrip.ui.invitation

sealed interface InvitePeopleEffect {
    data class ShowToastRes(val resId: Int) : InvitePeopleEffect
}
