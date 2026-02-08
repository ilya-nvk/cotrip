package nvk.cotrip.ui.aisuggestions

data class AiOptionUi(
    val label: String,
    val selected: Boolean,
)

data class AiSuggestionItemUi(
    val id: String,
    val title: String,
    val description: String,
    val typeLabel: String,
    val durationLabel: String,
    val budgetLabel: String,
    val estimatedCost: String,
    val isSaved: Boolean,
)
