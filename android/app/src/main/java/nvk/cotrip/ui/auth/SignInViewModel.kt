package nvk.cotrip.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val navigator: AppNavigator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SignInEffect>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val effects: SharedFlow<SignInEffect> = _effects.asSharedFlow()

    fun onEvent(event: SignInEvent) {
        when (event) {
            is SignInEvent.SignInWithGoogle -> signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        if (_uiState.value.isLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // TODO: заменить на реальный Google Sign-In
            delay(1500)

            val isSuccess = Math.random() > 0.2
            if (isSuccess) {
                navigator.navigate(Destination.Trips)
            } else {
                _effects.tryEmit(SignInEffect.ShowToast("Sign-in failed. Please try again."))
            }

            _uiState.update { it.copy(isLoading = false) }
        }
    }
}
