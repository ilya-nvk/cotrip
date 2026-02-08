package nvk.cotrip.ui.ideas

data class TripIdeasState(
    val tripId: String,
    val ideas: List<IdeaListItemUi>,
    val dayPicker: IdeaDayPickerState?,
)
