package nvk.cotrip.ui.idea.form

data class IdeaLocationSuggestionUi(
    val name: String,
    val placeId: String,
    val fullText: String,
)

data class IdeaFormState(
    val mode: IdeaFormMode,
    val ideaId: String?,
    val title: String,
    val city: String,
    val cityPlaceId: String?,
    val link: String,
    val citySuggestions: List<IdeaLocationSuggestionUi>,
    val isCitySearching: Boolean,
    val currencySymbol: String,
    val costAmount: String,
    val costType: IdeaCostType,
    val notes: String,
    val isSaving: Boolean,
)
