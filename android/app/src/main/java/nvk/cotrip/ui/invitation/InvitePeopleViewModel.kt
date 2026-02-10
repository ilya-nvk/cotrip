package nvk.cotrip.ui.invitation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.repository.InviteRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class InvitePeopleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val inviteRepository: InviteRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.InviteTravelers.ARG_TRIP_ID])

    private val _state = MutableStateFlow(
        InvitePeopleState(
            tripId = tripId,
            inviteLink = "",
            expiresInHours = 12,
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<InvitePeopleEffect>()
    val effects = _effects.asSharedFlow()

    init {
        loadInvite()
    }

    fun onEvent(event: InvitePeopleEvent) {
        when (event) {
            InvitePeopleEvent.OnCloseClick -> appNavigator.popBackStack()

            InvitePeopleEvent.OnCopyClick -> emitToast(R.string.invite_people_copied_toast)

            InvitePeopleEvent.OnShareClick -> emitToast(R.string.invite_people_share_not_implemented)
        }
    }

    private fun loadInvite() {
        viewModelScope.launch {
            val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    inviteRepository.createInvite(tripId)
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    val invite = result.data
                    val expiresAt =
                        runCatching { OffsetDateTime.parse(invite.expiresAt) }.getOrNull()
                    val hoursLeft = expiresAt?.let {
                        val diff = ChronoUnit.HOURS.between(OffsetDateTime.now(), it)
                        diff.coerceAtLeast(0)
                    } ?: 12

                    _state.value = InvitePeopleState(
                        tripId = tripId,
                        inviteLink = invite.url,
                        expiresInHours = hoursLeft.toInt(),
                    )
                }

                is ApiResult.Failure -> {
                    emitToast(uiErrorMapper.messageRes(result))
                }
            }
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(InvitePeopleEffect.ShowToastRes(resId)) }
    }
}
