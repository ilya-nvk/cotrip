package nvk.cotrip.ui.aisuggestions

data class AiCityPickerState(
    val cities: List<String>,
)

data class BuildRouteState(
    val tripId: String,
    val city: String?,
    val description: String,
    val typeOptions: List<AiOptionUi>,
    val timeOfDayOptions: List<AiOptionUi>,
    val budgetOptions: List<AiOptionUi>,
    val cityPicker: AiCityPickerState?,
)
