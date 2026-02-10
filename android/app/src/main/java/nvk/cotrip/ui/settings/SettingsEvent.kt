package nvk.cotrip.ui.settings

sealed interface SettingsEvent {
    data object OnBackClick : SettingsEvent
    data object OnSaveClick : SettingsEvent
    data object OnChangePhotoClick : SettingsEvent
    data object OnRemovePhotoClick : SettingsEvent
    data object OnNotificationsClick : SettingsEvent
    data class OnNameChange(val value: String) : SettingsEvent
    data class OnToggleNotifications(val key: String, val enabled: Boolean) : SettingsEvent
    data object OnLogoutClick : SettingsEvent
    data object OnDeleteProfileClick : SettingsEvent
    data object OnDismissDeleteDialog : SettingsEvent
    data object OnConfirmDeleteProfileClick : SettingsEvent
}
