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
import nvk.cotrip.data.network.dto.AuthDevRequest
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
            navigator.navigate(Destination.Trips)
        }
    }

    fun onEvent(event: SignInEvent) {
        when (event) {
            is SignInEvent.SignInWithGoogle -> signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = runCatching {
                withContext(Dispatchers.IO) {
                    api.devAuth(
                        AuthDevRequest(
                            googleId = "android-dev",
                            name = "Android Dev",
                        )
                    )
                }
            }

            result.onSuccess { response ->
                Log.d(TAG, "sign in success")
                sessionStore.setAccessToken(response.accessToken)
                navigator.navigate(Destination.Trips)
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
