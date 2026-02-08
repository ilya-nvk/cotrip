package nvk.cotrip.ui.outofrangedays

data class OutOfRangeDayUi(
    val id: String,
    val dayTitle: String,
    val dateText: String,
    val city: String?,
    val activitiesTitle: String,
    val activitiesPreview: List<String>,
)
