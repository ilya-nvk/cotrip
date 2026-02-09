package nvk.cotrip.ui.auth

sealed interface SignInEvent {
    data object StartGoogleSignIn : SignInEvent
    data class OnGoogleIdToken(val idToken: String) : SignInEvent
    data class OnGoogleSignInFailed(val message: String) : SignInEvent
}
