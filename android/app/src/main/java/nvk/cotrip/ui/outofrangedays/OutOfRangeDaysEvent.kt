package nvk.cotrip.ui.outofrangedays

sealed interface OutOfRangeDaysEvent {
    data object OnBackClick : OutOfRangeDaysEvent
    data object OnExtendEndClick : OutOfRangeDaysEvent
    data object OnRemoveClick : OutOfRangeDaysEvent
}
