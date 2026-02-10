package nvk.cotrip.ui.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
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
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.repository.AuthRepository
import nvk.cotrip.notifications.PushTokenSyncManager
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val navigator: AppNavigator,
    private val authRepository: AuthRepository,
    private val pushTokenSyncManager: PushTokenSyncManager,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SignInUiState())
    val uiState: StateFlow<SignInUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SignInEffect>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val effects: SharedFlow<SignInEffect> = _effects.asSharedFlow()

    init {
        if (authRepository.hasSession()) {
            navigator.navigate(Destination.Trips) {
                popUpTo(Destination.SignIn.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    fun onEvent(event: SignInEvent) {
        when (event) {
            SignInEvent.StartGoogleSignIn -> startGoogleSignIn()
            is SignInEvent.OnGoogleSignInResult -> handleGoogleResult(event.resultCode, event.data)
            is SignInEvent.OnGoogleIdToken -> signInWithGoogle(event.idToken)
            is SignInEvent.OnGoogleSignInFailed -> {
                _uiState.update { it.copy(isLoading = false) }
                _effects.tryEmit(SignInEffect.ShowToast(event.message))
            }
        }
    }

    private fun handleGoogleResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            onEvent(SignInEvent.OnGoogleSignInFailed("Sign-in canceled (code $resultCode)."))
            return
        }
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        runCatching {
            task.getResult(ApiException::class.java)
        }.onSuccess { account ->
            val token = account.idToken
            if (token.isNullOrBlank()) {
                onEvent(SignInEvent.OnGoogleSignInFailed("Missing idToken."))
            } else {
                onEvent(SignInEvent.OnGoogleIdToken(token))
            }
        }.onFailure {
            val apiException = it as? ApiException
            val code = apiException?.statusCode
            val message = apiException?.localizedMessage ?: it.localizedMessage ?: "Google sign-in failed."
            onEvent(
                SignInEvent.OnGoogleSignInFailed(
                    if (code != null) "Google sign-in failed ($code): $message" else message
                )
            )
        }
    }

    private fun startGoogleSignIn() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
    }

    private fun signInWithGoogle(idToken: String) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    authRepository.signInWithGoogle(idToken)
                }
            }) {
                is ApiResult.Success -> {
                    Log.d(TAG, "sign in success")
                    pushTokenSyncManager.syncIfPossible()
                    navigator.navigate(Destination.Trips) {
                        popUpTo(Destination.SignIn.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }

                is ApiResult.Failure -> {
                    Log.e(TAG, "sign in error", result.cause)
                    _effects.tryEmit(SignInEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    companion object {
        private const val TAG = "SignInViewModel"
    }
}
