package nvk.cotrip.ui.trip.members

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class TripMembersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val appNavigator: AppNavigator,
    private val tripRepository: TripRepository,
    private val userRepository: UserRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private val tripId: String =
        checkNotNull(savedStateHandle[Destination.TripMembers.ARG_TRIP_ID])

    private val _state = MutableStateFlow<TripMembersState>(TripMembersState.Loading)
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripMembersEffect>()
    val effects = _effects.asSharedFlow()

    init {
        observeMembers()
        refreshMembers(showErrorToast = false)
    }

    fun onEvent(event: TripMembersEvent) {
        when (event) {
            TripMembersEvent.OnBackClick -> appNavigator.popBackStack()
            TripMembersEvent.OnRefresh -> refreshMembers(showErrorToast = true)
            is TripMembersEvent.OnRemoveClick -> removeMember(event.memberId)
        }
    }

    private fun observeMembers() {
        viewModelScope.launch {
            val tripFlow = tripRepository.getTrip(tripId)
            val membersFlow = tripRepository.tripMembers(tripId)
            combine(
                tripFlow,
                membersFlow,
                userRepository.me
            ) { trip, members, me ->
                val myId = me?.id
                MembersPayload(
                    title = trip.title,
                    members = members.map {
                        TripMemberUi(
                            userId = it.userId,
                            name = it.name,
                            photoUrl = it.photoUrl,
                            initials = it.initials,
                            role = it.role,
                            status = it.status,
                        )
                    },
                    meId = myId,
                    isOwner = myId != null && trip.ownerId == myId,
                )
            }.collect { payload ->
                val isLoadingAction = (_state.value as? TripMembersState.Content)?.isLoadingAction
                    ?: false
                _state.value = TripMembersState.Content(
                    tripId = tripId,
                    title = payload.title,
                    members = payload.members,
                    meId = payload.meId,
                    isOwner = payload.isOwner,
                    isLoadingAction = isLoadingAction,
                )
            }
        }
    }

    private fun refreshMembers(showErrorToast: Boolean) {
        viewModelScope.launch {
            val current = _state.value as? TripMembersState.Content
            if (current != null) {
                _state.value = current.copy(isLoadingAction = true)
            }
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    tripRepository.refreshTrips().getOrThrow()
                    tripRepository.tripMembers(tripId).first()
                }
            }) {
                is ApiResult.Success -> Unit
                is ApiResult.Failure -> {
                    val latest = _state.value as? TripMembersState.Content
                    if (latest != null) {
                        _state.value = latest.copy(isLoadingAction = false)
                    }
                    if (showErrorToast) {
                        emit(TripMembersEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                    }
                }
            }
        }
    }

    private fun removeMember(memberId: String) {
        val snapshot = _state.value as? TripMembersState.Content ?: return
        if (snapshot.isLoadingAction) return
        viewModelScope.launch {
            _state.value = snapshot.copy(isLoadingAction = true)
            val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    tripRepository.removeMember(tripId, memberId)
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    val isSelf = memberId == snapshot.meId
                    val latest = _state.value as? TripMembersState.Content
                    if (latest != null) {
                        _state.value = latest.copy(isLoadingAction = false)
                    }
                    if (isSelf) {
                        emit(TripMembersEffect.ShowToastRes(R.string.trip_members_left_toast))
                        appNavigator.navigate(Destination.Trips) {
                            popUpTo(Destination.Trips.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        emit(TripMembersEffect.ShowToastRes(R.string.trip_members_removed_toast))
                    }
                }
                is ApiResult.Failure -> {
                    val latest = _state.value as? TripMembersState.Content
                    if (latest != null) {
                        _state.value = latest.copy(isLoadingAction = false)
                    }
                    emit(TripMembersEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun emit(effect: TripMembersEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }

    private data class MembersPayload(
        val title: String,
        val members: List<TripMemberUi>,
        val meId: String?,
        val isOwner: Boolean,
    )
}
