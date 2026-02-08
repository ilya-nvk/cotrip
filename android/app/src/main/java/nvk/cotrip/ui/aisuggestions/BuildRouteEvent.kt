package nvk.cotrip.ui.aisuggestions

sealed interface BuildRouteEvent {
    data object OnBackClick : BuildRouteEvent
    data object OnCityClick : BuildRouteEvent
    data object OnDismissCityPicker : BuildRouteEvent
    data class OnCitySelected(val city: String) : BuildRouteEvent
    data class OnDescriptionChange(val value: String) : BuildRouteEvent
    data class OnTypeToggle(val label: String) : BuildRouteEvent
    data class OnTimeOfDaySelect(val label: String) : BuildRouteEvent
    data class OnBudgetSelect(val label: String) : BuildRouteEvent
    data object OnGenerateClick : BuildRouteEvent
}
