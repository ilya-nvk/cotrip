package nvk.cotrip.ui.outofrangedays

sealed interface OutOfRangeDaysState {
    data object Loading : OutOfRangeDaysState

    data class Content(
        val tripId: String,
        val dateRangeText: String,
        val proposedEndDateText: String,
        val days: List<OutOfRangeDayUi>,
    ) : OutOfRangeDaysState
}
