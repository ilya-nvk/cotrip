package nvk.cotrip.ui.auth

sealed interface SignInEffect {
    data class ShowToast(val message: String) : SignInEffect
    data class ShowToastRes(val resId: Int) : SignInEffect
}
