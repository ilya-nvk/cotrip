package nvk.cotrip.ui.activity.details

data class ActivityDetailsState(
    val dayId: String,
    val activityId: String,
    val dayAndCity: String,
    val title: String,
    val dateText: String,
    val timeText: String,
    val locationName: String?,
    val link: String?,
    val costText: String?,
    val website: String?,
    val notes: String?,
)
