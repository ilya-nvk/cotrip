package nvk.cotrip.ui.activity.details

sealed interface ActivityDetailsState {
    data class Init(
        val activityId: String,
    ) : ActivityDetailsState

    data class Content(
        val dayId: String,
        val activityId: String,
        val isPastTrip: Boolean,
        val dayNumber: Int,
        val city: String?,
        val title: String,
        val dateText: String,
        val timeText: String,
        val locationName: String?,
        val link: String?,
        val costText: String?,
        val notes: String?,
    ) : ActivityDetailsState
}
