package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ExpenseParticipantDto(
    val userId: String,
    val shareAmount: Double? = null,
    val isIncluded: Boolean,
    val isPaid: Boolean,
)

@Serializable
data class ExpenseDto(
    val id: String,
    val tripId: String,
    val title: String,
    val amount: Double,
    val currencyCode: String,
    val status: String,
    val paidById: String? = null,
    val date: String? = null,
    val splitType: String,
    val note: String? = null,
    val participants: List<ExpenseParticipantDto> = emptyList(),
)
