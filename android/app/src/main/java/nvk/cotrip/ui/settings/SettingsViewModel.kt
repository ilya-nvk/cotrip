package nvk.cotrip.ui.settings

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
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appNavigator: AppNavigator,
    private val userRepository: UserRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private var originalName: String = ""

    private val _state = MutableStateFlow(
        SettingsState(
            profile = SettingsProfileUi(
                name = "",
                initials = "",
                hasPhoto = false
            ),
            notificationSections = listOf(
                SettingsNotificationSectionUi(
                    title = "DISCUSSIONS",
                    items = listOf(
                        SettingsToggleUi(
                            key = "discussions_comments",
                            title = "New comments in ideas",
                            enabled = true
                        )
                    )
                ),
                SettingsNotificationSectionUi(
                    title = "ITINERARY",
                    items = listOf(
                        SettingsToggleUi(
                            key = "itinerary_changes",
                            title = "Itinerary changes",
                            enabled = true
                        )
                    )
                ),
                SettingsNotificationSectionUi(
                    title = "EXPENSES",
                    items = listOf(
                        SettingsToggleUi(
                            key = "expenses_new",
                            title = "New expenses",
                            enabled = true
                        ),
                        SettingsToggleUi(
                            key = "expenses_settlements",
                            title = "Expense settlements",
                            enabled = true
                        )
                    )
                ),
                SettingsNotificationSectionUi(
                    title = "TRIPS",
                    items = listOf(
                        SettingsToggleUi(
                            key = "trips_added",
                            title = "Added to a trip",
                            enabled = true
                        ),
                        SettingsToggleUi(
                            key = "trips_date_changes",
                            title = "Trip date changes",
                            enabled = false
                        )
                    )
                )
            ),
            showDeleteDialog = false,
            isLoading = true,
            isSaving = false,
            canSave = false,
        )
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>()
    val effects = _effects.asSharedFlow()

    init {
        loadProfile()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.OnBackClick -> appNavigator.popBackStack()
            SettingsEvent.OnSaveClick -> saveProfile()
            SettingsEvent.OnChangePhotoClick -> {
                _state.update { current ->
                    current.copy(profile = current.profile.copy(hasPhoto = true))
                }
                emit(SettingsEffect.ShowToastRes(R.string.settings_photo_changed_toast))
            }

            SettingsEvent.OnRemovePhotoClick -> {
                _state.update { current ->
                    current.copy(profile = current.profile.copy(hasPhoto = false))
                }
            }

            is SettingsEvent.OnNameChange -> _state.update { current ->
                val name = event.value
                current.copy(
                    profile = current.profile.copy(name = name),
                    canSave = name.isNotBlank() && name != originalName
                )
            }

            is SettingsEvent.OnToggleNotifications -> _state.update { current ->
                current.copy(
                    notificationSections = current.notificationSections.map { section ->
                        section.copy(
                            items = section.items.map { item ->
                                if (item.key == event.key) item.copy(enabled = event.enabled) else item
                            }
                        )
                    }
                )
            }

            SettingsEvent.OnLogoutClick -> {
                userRepository.clearSession()
                appNavigator.navigate(Destination.SignIn) {
                    popUpTo(Destination.Trips.route) { inclusive = true }
                    launchSingleTop = true
                }
            }

            SettingsEvent.OnDeleteProfileClick -> _state.update { it.copy(showDeleteDialog = true) }
            SettingsEvent.OnDismissDeleteDialog -> _state.update { it.copy(showDeleteDialog = false) }
            SettingsEvent.OnConfirmDeleteProfileClick -> deleteProfile()
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            when (val result = apiCaller.call { withContext(Dispatchers.IO) { userRepository.getMe() } }) {
                is ApiResult.Success -> {
                    val user = result.data
                    originalName = user.name
                    _state.update {
                        it.copy(
                            profile = SettingsProfileUi(
                                name = user.name,
                                initials = user.initials,
                                hasPhoto = !user.photoUrl.isNullOrBlank()
                            ),
                            isLoading = false,
                            canSave = false,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false) }
                    emit(SettingsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun saveProfile() {
        val snapshot = _state.value
        if (!snapshot.canSave || snapshot.isSaving) return
        val name = snapshot.profile.name.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    userRepository.updateMe(UpdateUserRequest(name = name))
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    val user = result.data
                    originalName = user.name
                    _state.update {
                        it.copy(
                            profile = it.profile.copy(
                                name = user.name,
                                initials = user.initials,
                                hasPhoto = !user.photoUrl.isNullOrBlank()
                            ),
                            isSaving = false,
                            canSave = false,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    emit(SettingsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun deleteProfile() {
        viewModelScope.launch {
            _state.update { it.copy(showDeleteDialog = false, isSaving = true) }
            val result = apiCaller.call { withContext(Dispatchers.IO) { userRepository.deleteMe() } }
            when (result) {
                is ApiResult.Success -> {
                    userRepository.clearSession()
                    emit(SettingsEffect.ShowToastRes(R.string.settings_profile_deleted_toast))
                    appNavigator.navigate(Destination.SignIn) {
                        popUpTo(Destination.Trips.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    emit(SettingsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun emit(effect: SettingsEffect) {
        viewModelScope.launch { _effects.emit(effect) }
    }
}
