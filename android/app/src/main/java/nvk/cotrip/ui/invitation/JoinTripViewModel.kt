package nvk.cotrip.ui.invitation

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
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination

@HiltViewModel
class JoinTripViewModel @Inject constructor(
    private val appNavigator: AppNavigator,
    private val inviteRepository: InviteRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val _state = MutableStateFlow(JoinTripState(inviteInput = "", isLoading = false))
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<JoinTripEffect>()
    val effects = _effects.asSharedFlow()

    fun onEvent(event: JoinTripEvent) {
        when (event) {
            JoinTripEvent.OnBackClick -> appNavigator.popBackStack()
            is JoinTripEvent.OnInviteInputChange -> _state.update { it.copy(inviteInput = event.value) }
            JoinTripEvent.OnJoinClick -> joinTrip()
        }
    }

    private fun joinTrip() {
        val token = parseToken(_state.value.inviteInput)
        if (token.isNullOrBlank()) {
            emit(JoinTripEffect.ShowToastRes(R.string.join_trip_invalid))
            return
        }
        if (_state.value.isLoading) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    inviteRepository.acceptInvite(token)
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
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        val candidate = trimmed.substringAfterLast('/')
            .substringBefore('?')
            .substringBefore('#')
            .trim()
        return candidate.ifBlank { null }
    }

    private fun emit(effect: JoinTripEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
