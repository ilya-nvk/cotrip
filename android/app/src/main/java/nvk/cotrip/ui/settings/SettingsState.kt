package nvk.cotrip.ui.settings

data class SettingsState(
    val profile: SettingsProfileUi,
    val notificationSections: List<SettingsNotificationSectionUi>,
    val showDeleteDialog: Boolean,
)
