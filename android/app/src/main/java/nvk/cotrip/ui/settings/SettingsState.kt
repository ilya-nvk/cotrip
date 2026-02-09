package nvk.cotrip.ui.settings

data class SettingsState(
    val profile: SettingsProfileUi,
    val notificationSections: List<SettingsNotificationSectionUi>,
    val showDeleteDialog: Boolean,
    val isLoading: Boolean,
    val isSaving: Boolean,
    val canSave: Boolean,
)
