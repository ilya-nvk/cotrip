package nvk.cotrip.ui.auth

sealed interface SignInEvent {
    data object SignInWithGoogle : SignInEvent
}