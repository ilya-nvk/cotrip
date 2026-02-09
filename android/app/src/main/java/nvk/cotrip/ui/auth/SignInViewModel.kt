package nvk.cotrip.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.data.auth.SessionStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.AuthGoogleRequest
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val navigator: AppNavigator,
    private val api: CoTripApi,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SignInEffect>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val effects: SharedFlow<SignInEffect> = _effects.asSharedFlow()

    init {
        if (!sessionStore.getAccessToken().isNullOrBlank()) {
            navigator.navigate(Destination.Trips) {
                popUpTo(Destination.SignIn.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun onEvent(event: SignInEvent) {
        when (event) {
            SignInEvent.StartGoogleSignIn -> startGoogleSignIn()
            is SignInEvent.OnGoogleIdToken -> signInWithGoogle(event.idToken)
            is SignInEvent.OnGoogleSignInFailed -> {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(SignInEffect.ShowToast(event.message))
            }
        }
    }

    private fun startGoogleSignIn() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
    }

    private fun signInWithGoogle(idToken: String) {
        if (_uiState.value.isLoading.not()) {
            _uiState.update { it.copy(isLoading = true) }
        }

        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    api.googleAuth(AuthGoogleRequest(idToken))
                }
            }

            result.onSuccess { response ->
                Log.d(TAG, "sign in success")
                sessionStore.setAccessToken(response.accessToken)
                navigator.navigate(Destination.Trips) {
                    popUpTo(Destination.SignIn.route) { inclusive = true }
                    launchSingleTop = true
                }
            }.onFailure {
                Log.e(TAG, "sign in error", it)
                _effects.tryEmit(SignInEffect.ShowToast("Sign-in failed. Please try again."))
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    companion object {
        private const val TAG = "SignInViewModel"
    }
}
