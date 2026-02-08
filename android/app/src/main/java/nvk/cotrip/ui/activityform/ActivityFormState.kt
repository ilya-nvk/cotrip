package nvk.cotrip.ui.activityform

data class ActivityFormState(
    val mode: ActivityFormMode,
    val activityId: String?,
    val headerText: String?,
    val title: String,
    val dateText: String,
    val timeText: String,
    val locationName: String,
    val locationLink: String,
    val currencySymbol: String,
    val costAmount: String,
    val costType: CostType,
    val website: String,
    val notes: String,
    val isSaving: Boolean,
)
