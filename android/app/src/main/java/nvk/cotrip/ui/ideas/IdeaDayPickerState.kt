package nvk.cotrip.ui.ideas

data class IdeaDayOptionUi(
    val id: String,
    val dayNumber: Int,
    val dateText: String,
    val city: String,
)

data class IdeaDayPickerState(
    val ideaId: String,
    val days: List<IdeaDayOptionUi>,
)
