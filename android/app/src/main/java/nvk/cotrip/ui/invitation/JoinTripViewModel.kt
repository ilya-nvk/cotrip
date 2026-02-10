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
private val TRIP_ID_REGEX = Regex("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$", RegexOption.IGNORE_CASE)

private sealed interface JoinTarget {
    data class InviteToken(val token: String) : JoinTarget
    data class TripId(val tripId: String) : JoinTarget
}

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

    private val deepLinkValue: String? =
        savedStateHandle[Destination.JoinTrip.ARG_INVITE_TOKEN]

    init {
        val target = deepLinkValue?.let { parseJoinTarget(it) }
        if (target != null) {
            _state.update {
                it.copy(
                    inviteInput = canonicalInput(target),
                    isInviteValid = true,
                )
            }
            joinTrip(targetOverride = target)
        }
    }

    fun onEvent(event: JoinTripEvent) {
        when (event) {
            JoinTripEvent.OnBackClick -> appNavigator.popBackStack()
            is JoinTripEvent.OnInviteInputChange -> {
                val isValid = parseJoinTarget(event.value) != null
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

    private fun joinTrip(targetOverride: JoinTarget? = null) {
        val target = targetOverride ?: parseJoinTarget(_state.value.inviteInput)
        if (target == null) {
            emit(JoinTripEffect.ShowToastRes(R.string.join_trip_invalid))
            return
        }
        if (_state.value.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val preflight = apiCaller.call {
                withContext(Dispatchers.IO) {
                    runCatching { tripRepository.refreshTrips() }
                    val joinedIds = tripRepository.listTrips().mapTo(mutableSetOf()) { it.id }
                    when (target) {
                        is JoinTarget.InviteToken -> {
                            val invite = inviteRepository.getInvite(target.token)
                            joinedIds.contains(invite.tripId)
                        }
                        is JoinTarget.TripId -> joinedIds.contains(target.tripId)
                    }
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
                    val tripId = when (target) {
                        is JoinTarget.InviteToken -> inviteRepository.acceptInvite(target.token)
                        is JoinTarget.TripId -> inviteRepository.joinTripById(target.tripId)
                    }.ifBlank {
                        when (target) {
                            is JoinTarget.InviteToken -> error("acceptInvite returned empty tripId")
                            is JoinTarget.TripId -> target.tripId
                        }
                    }
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

    private fun parseJoinTarget(raw: String): JoinTarget? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        trimmed.toTokenTarget()?.let { return it }
        trimmed.toTripIdTarget()?.let { return it }

        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase(Locale.US) ?: return null
        if (host != INVITE_HOST) return null
        val pathSegments = uri.pathSegments.filter { it.isNotBlank() }
        if (pathSegments.isEmpty()) return null

        return when {
            pathSegments.size >= 2 &&
                pathSegments[0].equals("invite", ignoreCase = true) ->
                pathSegments[1].toTokenTarget()

            pathSegments.size >= 3 &&
                pathSegments[0].equals("v1", ignoreCase = true) &&
                pathSegments[1].equals("invites", ignoreCase = true) ->
                pathSegments[2].toTokenTarget()

            pathSegments.size >= 3 &&
                pathSegments[0].equals("trips", ignoreCase = true) &&
                pathSegments[2].equals("invite", ignoreCase = true) ->
                pathSegments[1].toTripIdTarget()

            pathSegments.size >= 4 &&
                pathSegments[0].equals("v1", ignoreCase = true) &&
                pathSegments[1].equals("trips", ignoreCase = true) &&
                pathSegments[3].equals("invite", ignoreCase = true) ->
                pathSegments[2].toTripIdTarget()

            else -> null
        }
    }

    private fun String.toTokenTarget(): JoinTarget.InviteToken? {
        val normalized = trim().lowercase(Locale.US)
        return normalized.takeIf { isValidToken(it) }?.let { JoinTarget.InviteToken(it) }
    }

    private fun String.toTripIdTarget(): JoinTarget.TripId? {
        val normalized = trim().lowercase(Locale.US)
        return normalized.takeIf { isValidTripId(it) }?.let { JoinTarget.TripId(it) }
    }

    private fun isValidToken(token: String): Boolean {
        return INVITE_TOKEN_REGEX.matches(token.trim())
    }

    private fun isValidTripId(tripId: String): Boolean {
        return TRIP_ID_REGEX.matches(tripId.trim())
    }

    private fun canonicalInput(target: JoinTarget): String {
        return when (target) {
            is JoinTarget.InviteToken -> canonicalInviteUrl(target.token)
            is JoinTarget.TripId -> target.tripId
        }
    }

    private fun canonicalInviteUrl(token: String): String {
        return "https://$INVITE_HOST/invite/${token.trim()}"
    }

    private fun emit(effect: JoinTripEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
