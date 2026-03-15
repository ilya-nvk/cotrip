package nvk.cotrip.ui.activity.form

import nvk.cotrip.ui.common.LimitDialogState
import java.time.LocalDate

data class LocationSuggestionUi(
    val name: String,
    val placeId: String,
    val fullText: String,
)

data class ActivityFormState(
    val mode: ActivityFormMode,
    val activityId: String?,
    val headerDayNumber: Int?,
    val headerCity: String?,
    val tripStartDate: LocalDate?,
    val tripEndDate: LocalDate?,
    val title: String,
    val dateText: String,
    val timeText: String,
    val locationInput: String,
    val locationPlaceId: String?,
    val linkInput: String,
    val locationSuggestions: List<LocationSuggestionUi>,
    val isLocationSearching: Boolean,
    val currencySymbol: String,
    val costAmount: String,
    val costType: CostType,
    val notes: String,
    val isSaving: Boolean,
    val limitDialog: LimitDialogState? = null,
)
