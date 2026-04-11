package nvk.cotrip.data.sync

import kotlinx.serialization.Serializable
import nvk.cotrip.data.network.dto.ExpenseParticipantInput
import nvk.cotrip.data.network.dto.NotificationSettingDto

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

@Serializable
data class SyncTripMemberDeletePayload(
    val tripId: String,
    val memberId: String,
)

@Serializable
data class SyncIdeaStatusUpsertPayload(
    val status: String,
)

@Serializable
data class SyncIdeaConvertCreatePayload(
    val dayId: String,
    val timeText: String? = null,
    val orderIndex: Int? = null,
)

@Serializable
data class SyncActivityReorderUpsertPayload(
    val dayId: String,
    val orderedIds: List<String>,
)

@Serializable
data class SyncItineraryTrimUpsertPayload(
    val tripId: String,
    val action: String,
    val dayIds: List<String>,
)

@Serializable
data class SyncNotificationSettingsUpsertPayload(
    val items: List<NotificationSettingDto>,
)

@Serializable
data class SyncNotificationReadUpsertPayload(
    val mode: String,
    val notificationId: String? = null,
    val ideaId: String? = null,
)

@Serializable
data class SyncUserProfileUpsertPayload(
    val name: String,
    val photoUrl: String? = null,
)

@Serializable
data class SyncAiSuggestionSaveUpsertPayload(
    val suggestionId: String,
)
