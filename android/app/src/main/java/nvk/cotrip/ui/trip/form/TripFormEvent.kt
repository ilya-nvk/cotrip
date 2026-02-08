package nvk.cotrip.ui.trip.form

sealed interface TripFormEvent {
    data object OnCloseClick : TripFormEvent
    data object OnCancelClick : TripFormEvent

    data object OnPickCoverClick : TripFormEvent

    data class OnNameChange(val value: String) : TripFormEvent
    data object OnStartDateClick : TripFormEvent
    data object OnEndDateClick : TripFormEvent
    data class OnDescriptionChange(val value: String) : TripFormEvent
    data class OnCurrencySelect(val currency: TripCurrency) : TripFormEvent

    data object OnPrimaryActionClick : TripFormEvent

    data object OnArchiveClick : TripFormEvent
    data object OnDeleteClick : TripFormEvent
}
