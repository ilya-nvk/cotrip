package nvk.cotrip.ui.ideaform

data class IdeaFormState(
    val mode: IdeaFormMode,
    val ideaId: String?,
    val title: String,
    val city: String,
    val currencySymbol: String,
    val costAmount: String,
    val costType: IdeaCostType,
    val website: String,
    val notes: String,
    val isSaving: Boolean,
    val cityPicker: IdeaCityPickerState?,
)
