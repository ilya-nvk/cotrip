package nvk.cotrip.ui.outofrangedays

data class OutOfRangeDaysState(
    val tripId: String,
    val dateRangeText: String,
    val proposedEndDateText: String,
    val days: List<OutOfRangeDayUi>,
)
