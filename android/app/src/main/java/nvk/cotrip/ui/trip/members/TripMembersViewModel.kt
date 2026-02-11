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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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

    private val _state = MutableStateFlow(
        TripMembersState(
            tripId = tripId,
            title = "",
            members = emptyList(),
            meId = null,
            isOwner = false,
            isLoading = true,
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TripMembersEffect>()
    val effects = _effects.asSharedFlow()

    init {
        loadMembers()
    }

    fun onEvent(event: TripMembersEvent) {
        when (event) {
            TripMembersEvent.OnBackClick -> appNavigator.popBackStack()
            TripMembersEvent.OnRefresh -> loadMembers()
            is TripMembersEvent.OnRemoveClick -> removeMember(event.memberId)
        }
    }

    private fun loadMembers() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    val trip = tripRepository.getTrip(tripId).first()
                    val members = tripRepository.tripMembers(tripId).first()
                    val me = checkNotNull(userRepository.me.first())
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
                        meId = me.id,
                        isOwner = trip.ownerId == me.id,
                    )
                }
            }

            when (result) {
                is ApiResult.Success -> {
                    val payload = result.data
                    _state.update {
                        it.copy(
                            title = payload.title,
                            members = payload.members,
                            meId = payload.meId,
                            isOwner = payload.isOwner,
                            isLoading = false,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false) }
                    emit(TripMembersEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun removeMember(memberId: String) {
        val snapshot = _state.value
        if (snapshot.isLoading) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    tripRepository.removeMember(tripId, memberId)
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    val isSelf = memberId == snapshot.meId
                    _state.update {
                        it.copy(
                            members = it.members.filterNot { member -> member.userId == memberId },
                            isLoading = false,
                        )
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
                    _state.update { it.copy(isLoading = false) }
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
        val meId: String,
        val isOwner: Boolean,
    )
}
