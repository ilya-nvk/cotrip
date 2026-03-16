package nvk.cotrip.data.sync

import kotlinx.serialization.Serializable
import nvk.cotrip.data.network.dto.ExpenseParticipantInput

@Serializable
data class SyncTripCreateDayPayload(
    val id: String,
    val date: String,
    val dayNumber: Int,
)

@Serializable
data class SyncTripCreatePayload(
    val title: String,
    val description: String? = null,
    val startDate: String,
    val endDate: String,
    val locationLine: String? = null,
    val coverUrl: String? = null,
    val currencyCode: String,
    val days: List<SyncTripCreateDayPayload>,
)

@Serializable
data class SyncIdeaCreatePayload(
    val tripId: String,
    val title: String,
    val city: String? = null,
    val link: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val notes: String? = null,
)

@Serializable
data class SyncExpenseCreatePayload(
    val tripId: String,
    val title: String,
    val amount: Double,
    val currencyCode: String? = null,
    val status: String,
    val paidById: String? = null,
    val date: String? = null,
    val splitType: String,
    val note: String? = null,
    val participants: List<ExpenseParticipantInput> = emptyList(),
)

@Serializable
data class SyncActivityCreatePayload(
    val dayId: String,
    val title: String,
    val timeText: String? = null,
    val locationName: String? = null,
    val link: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val notes: String? = null,
    val orderIndex: Int? = null,
)
