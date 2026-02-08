package nvk.cotrip.ui.ideas

sealed interface TripIdeasEvent {
    data object OnBackClick : TripIdeasEvent
    data object OnAddIdeaClick : TripIdeasEvent
    data class OnIdeaClick(val ideaId: String) : TripIdeasEvent
    data class OnAddToItineraryClick(val ideaId: String) : TripIdeasEvent
    data object OnDismissDayPicker : TripIdeasEvent
    data class OnDaySelected(val day: IdeaDayOptionUi) : TripIdeasEvent
}
