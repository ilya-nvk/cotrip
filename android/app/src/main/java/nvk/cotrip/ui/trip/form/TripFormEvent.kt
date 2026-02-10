package nvk.cotrip.ui.trip.form

sealed interface TripFormEvent {
    data object OnCloseClick : TripFormEvent
    data object OnCancelClick : TripFormEvent

    data object OnPickCoverClick : TripFormEvent
    data class OnCoverPicked(val uriString: String?) : TripFormEvent

    data class OnNameChange(val value: String) : TripFormEvent
    data class OnStartDateSelected(val date: java.time.LocalDate) : TripFormEvent
    data class OnEndDateSelected(val date: java.time.LocalDate) : TripFormEvent
    data class OnDescriptionChange(val value: String) : TripFormEvent
    data class OnCurrencySelect(val currency: TripCurrency) : TripFormEvent

    data object OnPrimaryActionClick : TripFormEvent

    data object OnArchiveClick : TripFormEvent
    data object OnDeleteClick : TripFormEvent
}
