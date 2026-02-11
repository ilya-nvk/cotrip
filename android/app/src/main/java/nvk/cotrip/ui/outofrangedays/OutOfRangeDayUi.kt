package nvk.cotrip.ui.outofrangedays

data class OutOfRangeDayUi(
    val id: String,
    val dayNumber: Int,
    val dateText: String,
    val city: String?,
    val activitiesCount: Int,
    val activitiesPreview: List<String>,
    val hiddenActivitiesCount: Int,
)
