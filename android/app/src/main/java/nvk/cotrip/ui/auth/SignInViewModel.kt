package nvk.cotrip.ui.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
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
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.refresh.RefreshScheduler
import nvk.cotrip.data.repository.AuthRepository
import nvk.cotrip.notifications.PushTokenSyncManager
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val navigator: AppNavigator,
    private val authRepository: AuthRepository,
    private val refreshScheduler: RefreshScheduler,
    private val pushTokenSyncManager: PushTokenSyncManager,
    private val apiCaller: ApiCaller,
    private val networkStateProvider: NetworkStateProvider,
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
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        runCatching {
            task.getResult(ApiException::class.java)
        }.onSuccess { account ->
            val token = account.idToken
            if (token.isNullOrBlank()) {
                onEvent(
                    SignInEvent.OnGoogleSignInFailed(
                        appContext.getString(R.string.sign_in_error_missing_id_token)
                    )
                )
            } else {
                onEvent(SignInEvent.OnGoogleIdToken(token))
            }
        }.onFailure {
            val apiException = it as? ApiException
            val code = apiException?.statusCode
            val message = when {
                code != null -> mapGoogleSignInError(
                    code = code,
                    fallbackMessage = apiException.localizedMessage
                        ?: it.localizedMessage
                        ?: appContext.getString(R.string.sign_in_error_google_failed)
                )

                resultCode != Activity.RESULT_OK -> appContext.getString(
                    R.string.sign_in_error_canceled_with_code,
                    resultCode
                )

                else -> it.localizedMessage
                    ?: appContext.getString(R.string.sign_in_error_google_failed)
            }
            if (code != null) {
                Log.w(TAG, "Google sign-in failed with statusCode=$code resultCode=$resultCode", it)
            }
            onEvent(
                SignInEvent.OnGoogleSignInFailed(message)
            )
        }
    }

    private fun startGoogleSignIn() {
        if (_uiState.value.isLoading) return
        if (!networkStateProvider.isOnline()) {
            _effects.tryEmit(SignInEffect.ShowToastRes(R.string.common_error_network))
            return
        }
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
                    refreshScheduler.scheduleImmediate()
                    withContext(Dispatchers.IO) {
                        pushTokenSyncManager.syncCurrentToken()
                    }
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

    private fun mapGoogleSignInError(code: Int, fallbackMessage: String): String {
        return when (code) {
            CommonStatusCodes.NETWORK_ERROR -> appContext.getString(R.string.sign_in_error_google_network)
            CommonStatusCodes.DEVELOPER_ERROR -> appContext.getString(R.string.sign_in_error_google_misconfigured)
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> appContext.getString(
                R.string.sign_in_error_canceled_with_code,
                code
            )

            GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS ->
                appContext.getString(R.string.sign_in_error_google_in_progress)

            else -> appContext.getString(
                R.string.sign_in_error_google_failed_with_code,
                code,
                fallbackMessage
            )
        }
    }
}
