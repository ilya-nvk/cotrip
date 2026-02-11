package nvk.cotrip.ui.settings

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
import nvk.cotrip.data.network.dto.NotificationSettingDto
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.repository.ImageUploadRepository
import nvk.cotrip.data.repository.NotificationRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appNavigator: AppNavigator,
    private val userRepository: UserRepository,
    private val imageUploadRepository: ImageUploadRepository,
    private val notificationRepository: NotificationRepository,
    private val apiCaller: ApiCaller,
    private val uiErrorMapper: UiErrorMapper,
) : ViewModel() {

    private var originalName: String = ""
    private var originalPhotoUrl: String? = null

    private val _state = MutableStateFlow(
        SettingsState(
            profile = SettingsProfileUi(
                name = "",
                initials = "",
                hasPhoto = false,
                photoUrl = null,
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
        loadNotificationSettings()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            SettingsEvent.OnBackClick -> appNavigator.popBackStack()
            SettingsEvent.OnSaveClick -> saveProfile()
            SettingsEvent.OnChangePhotoClick -> emit(SettingsEffect.OpenImagePicker)

            is SettingsEvent.OnPhotoPicked -> {
                val uri = event.uriString?.trim().orEmpty()
                if (uri.isNotBlank()) {
                    uploadPhoto(uri)
                }
            }

            SettingsEvent.OnRemovePhotoClick -> {
                _state.update { current ->
                    val updatedProfile = current.profile.copy(
                        hasPhoto = false,
                        photoUrl = null,
                    )
                    current.copy(
                        profile = updatedProfile,
                        canSave = canSaveProfileChanges(updatedProfile.name, updatedProfile.photoUrl)
                    )
                }
            }

            is SettingsEvent.OnNameChange -> _state.update { current ->
                val name = event.value
                val profile = current.profile.copy(name = name)
                current.copy(
                    profile = profile,
                    canSave = canSaveProfileChanges(name, profile.photoUrl)
                )
            }

            is SettingsEvent.OnToggleNotifications -> _state.update { current ->
                val updated = current.copy(
                    notificationSections = current.notificationSections.map { section ->
                        section.copy(
                            items = section.items.map { item ->
                                if (item.key == event.key) item.copy(enabled = event.enabled) else item
                            }
                        )
                    }
                )
                updateNotificationSettings(previous = current, updated = updated)
                updated
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
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    userRepository.refreshMe().getOrThrow()
                    checkNotNull(userRepository.me.first())
                }
            }) {
                is ApiResult.Success -> {
                    val user = result.data
                    originalName = user.name
                    originalPhotoUrl = normalizePhotoUrl(user.photoUrl)
                    _state.update {
                        it.copy(
                            profile = SettingsProfileUi(
                                name = user.name,
                                initials = user.initials,
                                hasPhoto = !user.photoUrl.isNullOrBlank(),
                                photoUrl = normalizePhotoUrl(user.photoUrl),
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

    private fun loadNotificationSettings() {
        viewModelScope.launch {
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    notificationRepository.refreshSettings().getOrThrow()
                    notificationRepository.settings.first()
                }
            }) {
                is ApiResult.Success -> applyNotificationSettings(result.data)
                is ApiResult.Failure ->
                    emit(SettingsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
            }
        }
    }

    private fun applyNotificationSettings(settings: List<NotificationSettingDto>) {
        val byKey = settings.associateBy { it.key }
        _state.update { current ->
            current.copy(
                notificationSections = current.notificationSections.map { section ->
                    section.copy(
                        items = section.items.map { item ->
                            val serverItem = byKey[item.key]
                            if (serverItem != null) item.copy(enabled = serverItem.enabled) else item
                        }
                    )
                }
            )
        }
    }

    private fun updateNotificationSettings(
        previous: SettingsState,
        updated: SettingsState,
    ) {
        viewModelScope.launch {
            val payload = updated.notificationSections.flatMap { section ->
                section.items.map { item ->
                    NotificationSettingDto(key = item.key, enabled = item.enabled)
                }
            }
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) {
                    notificationRepository.updateSettings(payload).getOrThrow()
                    notificationRepository.settings.first()
                }
            }) {
                is ApiResult.Success -> applyNotificationSettings(result.data)
                is ApiResult.Failure -> {
                    _state.value = previous
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
                    userRepository.updateMe(
                        UpdateUserRequest(
                            name = name,
                            photoUrl = snapshot.profile.photoUrl ?: "",
                        )
                    )
                }
            }
            when (result) {
                is ApiResult.Success -> {
                    val user = result.data
                    originalName = user.name
                    originalPhotoUrl = normalizePhotoUrl(user.photoUrl)
                    _state.update {
                        val photoUrl = normalizePhotoUrl(user.photoUrl)
                        it.copy(
                            profile = it.profile.copy(
                                name = user.name,
                                initials = user.initials,
                                hasPhoto = !photoUrl.isNullOrBlank(),
                                photoUrl = photoUrl,
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

    private fun uploadPhoto(uriString: String) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            when (val result = apiCaller.call {
                withContext(Dispatchers.IO) { imageUploadRepository.uploadImage(uriString) }
            }) {
                is ApiResult.Success -> {
                    _state.update { current ->
                        val updatedProfile = current.profile.copy(
                            hasPhoto = true,
                            photoUrl = result.data
                        )
                        current.copy(
                            profile = updatedProfile,
                            isSaving = false,
                            canSave = canSaveProfileChanges(updatedProfile.name, updatedProfile.photoUrl)
                        )
                    }
                    emit(SettingsEffect.ShowToastRes(R.string.settings_photo_changed_toast))
                }

                is ApiResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    emit(SettingsEffect.ShowToastRes(uiErrorMapper.messageRes(result)))
                }
            }
        }
    }

    private fun canSaveProfileChanges(name: String, photoUrl: String?): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return false
        val nameChanged = normalizedName != originalName.trim()
        val photoChanged = normalizePhotoUrl(photoUrl) != originalPhotoUrl
        return nameChanged || photoChanged
    }

    private fun normalizePhotoUrl(photoUrl: String?): String? {
        return photoUrl?.trim()?.takeIf { it.isNotBlank() }
    }
}
