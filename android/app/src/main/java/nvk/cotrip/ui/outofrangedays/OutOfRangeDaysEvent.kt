package nvk.cotrip.ui.outofrangedays

sealed interface OutOfRangeDaysEvent {
    data object OnBackClick : OutOfRangeDaysEvent
    data object OnKeepClick : OutOfRangeDaysEvent
    data object OnRemoveClick : OutOfRangeDaysEvent
}
