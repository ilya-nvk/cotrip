package nvk.cotrip.ui.invitation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.repository.InviteRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.util.Locale

private const val INVITE_HOST = "api.cotrip.site"
private val INVITE_TOKEN_REGEX = Regex("^[a-f0-9]{32}$", RegexOption.IGNORE_CASE)

@HiltViewModel
class JoinTripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val inviteRepository: InviteRepository,
    private val tripRepository: TripRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val _state = MutableStateFlow(
        JoinTripState(
            inviteInput = "",
            isLoading = false,
            isInviteValid = false,
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<JoinTripEffect>()
    val effects = _effects.asSharedFlow()

    private val deepLinkToken: String? =
        savedStateHandle[Destination.JoinTrip.ARG_INVITE_TOKEN]

    init {
        val token = deepLinkToken?.trim()?.takeIf { it.isNotBlank() }
        if (token != null && isValidToken(token)) {
            val canonicalUrl = canonicalInviteUrl(token)
            _state.update {
                it.copy(
                    inviteInput = canonicalUrl,
                    isInviteValid = true,
                )
            }
            joinTrip(tokenOverride = token)
        }
    }

    fun onEvent(event: JoinTripEvent) {
        when (event) {
            JoinTripEvent.OnBackClick -> appNavigator.popBackStack()
            is JoinTripEvent.OnInviteInputChange -> {
                val isValid = parseToken(event.value) != null
                _state.update {
                    it.copy(
                        inviteInput = event.value,
                        isInviteValid = isValid,
                    )
                }
            }
            JoinTripEvent.OnJoinClick -> joinTrip()
        }
    }

    private fun joinTrip(tokenOverride: String? = null) {
        val token = tokenOverride ?: parseToken(_state.value.inviteInput)
        if (token.isNullOrBlank()) {
            emit(JoinTripEffect.ShowToastRes(R.string.join_trip_invalid))
            return
        }
        if (_state.value.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val preflight = apiCaller.call {
                withContext(Dispatchers.IO) {
                    val invite = inviteRepository.getInvite(token)
                    runCatching { tripRepository.refreshTrips() }
                    tripRepository.listTrips().any { it.id == invite.tripId }
                }
            }

            when (preflight) {
                is ApiResult.Success -> {
                    if (preflight.data) {
                        _state.update { it.copy(isLoading = false) }
                        emit(JoinTripEffect.ShowToastRes(R.string.join_trip_already_joined))
                        return@launch
                    }
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false) }
                    emit(JoinTripEffect.ShowToastRes(uiErrorMapper.messageRes(preflight)))
                    return@launch
                }
            }

            val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    val tripId = inviteRepository.acceptInvite(token)
                    runCatching { tripRepository.refreshTrips() }
                    tripId
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    val tripId = result.data
                    emit(JoinTripEffect.ShowToastRes(R.string.join_trip_success))
                    appNavigator.navigate(Destination.TripDetails(tripId)) {
                        popUpTo(Destination.Trips.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false) }
                    emit(JoinTripEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun parseToken(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        if (host != INVITE_HOST) return null
        val pathSegments = uri.pathSegments
        if (pathSegments.size < 2) return null
        if (!pathSegments[0].equals("invite", ignoreCase = true)) return null
        val token = pathSegments[1].trim()
        return token.takeIf { isValidToken(it) }
    }

    private fun isValidToken(token: String): Boolean {
        return INVITE_TOKEN_REGEX.matches(token.trim())
    }

    private fun canonicalInviteUrl(token: String): String {
        return "https://$INVITE_HOST/invite/${token.trim()}"
    }

    private fun emit(effect: JoinTripEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
