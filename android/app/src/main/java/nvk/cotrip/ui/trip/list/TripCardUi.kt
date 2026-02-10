package nvk.cotrip.ui.trip.list

import androidx.compose.runtime.Immutable
import nvk.cotrip.ui.components.AvatarStackItem

@Immutable
data class TripCardUi(
    val id: String,
    val title: String,
    val dateRange: String,
    val locationLine: String,
    val peopleCountText: String,
    val avatars: List<AvatarStackItem>,
    val isInProgress: Boolean = false,
    val coverUrl: String? = null,
)
