package nvk.cotrip.ui.settings

sealed interface SettingsEffect {
    data class ShowToastRes(val resId: Int) : SettingsEffect
}
