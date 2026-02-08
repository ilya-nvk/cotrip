package nvk.cotrip.ui.trip.list

import androidx.compose.runtime.Immutable

@Immutable
data class TripCardUi(
    val id: String,
    val title: String,
    val dateRange: String,
    val locationLine: String,
    val peopleCountText: String,
    val initials: List<String>,
    val isInProgress: Boolean = false,
    val coverUrl: String? = null,
)