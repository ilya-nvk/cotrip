package nvk.cotrip.ui.activitydetails

data class ActivityDetailsState(
    val dayId: String,
    val activityId: String,
    val dayAndCity: String,
    val title: String,
    val dateText: String,
    val timeText: String,
    val locationName: String?,
    val costText: String?,
    val website: String?,
    val notes: String?,
)
