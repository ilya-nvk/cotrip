package nvk.cotrip.ui.idea.common

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
