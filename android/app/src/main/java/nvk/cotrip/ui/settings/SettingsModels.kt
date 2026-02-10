package nvk.cotrip.ui.settings

data class SettingsProfileUi(
    val name: String,
    val initials: String,
    val hasPhoto: Boolean,
    val photoUrl: String? = null,
)

data class SettingsToggleUi(
    val key: String,
    val title: String,
    val enabled: Boolean,
)

data class SettingsNotificationSectionUi(
    val title: String,
    val items: List<SettingsToggleUi>,
)
