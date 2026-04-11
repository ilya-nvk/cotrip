package nvk.cotrip.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nvk.cotrip.R
import nvk.cotrip.data.network.ApiCaller
import nvk.cotrip.data.network.ApiResult
import nvk.cotrip.data.network.dto.NotificationSettingDto
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.repository.AuthRepository
import nvk.cotrip.data.repository.ImageUploadRepository
import nvk.cotrip.data.repository.NotificationRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.ui.common.TextInputLimits
import nvk.cotrip.ui.common.UiErrorMapper
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val appNavigator: AppNavigator,
    private val authRepository: AuthRepository,
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
                    title = appContext.getString(R.string.settings_notifications_discussions),
                    items = listOf(
                        SettingsToggleUi(
                            key = "discussions_comments",
                            title = appContext.getString(R.string.settings_notification_new_comments),
                            enabled = true
                        )
                    )
                ),
                SettingsNotificationSectionUi(
                    title = appContext.getString(R.string.settings_notifications_expenses),
                    items = listOf(
                        SettingsToggleUi(
                            key = "expenses_new",
                            title = appContext.getString(R.string.settings_notification_new_expenses),
                            enabled = true
                        ),
                        SettingsToggleUi(
                            key = "expenses_settlements",
                            title = appContext.getString(R.string.settings_notification_expense_settlements),
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

    private val _effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 8)
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
                val name = event.value.take(TextInputLimits.SETTINGS_NAME)
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
                viewModelScope.launch {
                    runCatching { authRepository.logout() }
                    authRepository.clearSession()
                    appNavigator.navigate(Destination.SignIn) {
                        popUpTo(Destination.Trips.route) { inclusive = true }
                        launchSingleTop = true
                    }
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
                userRepository.refreshMe().getOrThrow()
                checkNotNull(userRepository.me.first())
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
                notificationRepository.refreshSettings().getOrThrow()
                notificationRepository.settings.first()
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
                notificationRepository.updateSettings(payload).getOrThrow()
                notificationRepository.settings.first()
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
                userRepository.updateMe(
                    UpdateUserRequest(
                        name = name,
                        photoUrl = snapshot.profile.photoUrl ?: "",
                    )
                )
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
            val result = apiCaller.call { userRepository.deleteMe() }
            when (result) {
                is ApiResult.Success -> {
                    userRepository.clearSession()
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
                imageUploadRepository.uploadImage(uriString)
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
