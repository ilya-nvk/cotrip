package nvk.cotrip.ui.auth

sealed interface SignInEffect {
    data class ShowToast(val message: String) : SignInEffect
}