package nvk.cotrip.ui.invitation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class InvitePeopleViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.InviteTravelers.ARG_TRIP_ID])

    private val _state = MutableStateFlow(
        InvitePeopleState(
            tripId = tripId,
            inviteLink = "https://tripapp.com/invite/xyz123abc456",
            expiresInHours = 12
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<InvitePeopleEffect>()
    val effects = _effects.asSharedFlow()

    fun onEvent(event: InvitePeopleEvent) {
        when (event) {
            InvitePeopleEvent.OnCloseClick -> appNavigator.popBackStack()

            InvitePeopleEvent.OnCopyClick -> emitToast(R.string.invite_people_copied_toast)

            InvitePeopleEvent.OnShareClick -> emitToast(R.string.invite_people_share_not_implemented)
        }
    }

    private fun emitToast(resId: Int) {
        viewModelScope.launch { _effects.emit(InvitePeopleEffect.ShowToastRes(resId)) }
    }
}
