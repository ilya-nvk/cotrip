package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ExpenseParticipantDto(
    val userId: String,
    val shareAmount: Double? = null,
    val isIncluded: Boolean,
    val isPaid: Boolean,
    val name: String? = null,
)

@Serializable
data class ExpenseParticipantInput(
    val userId: String,
    val shareAmount: Double? = null,
    val isIncluded: Boolean = true,
    val isPaid: Boolean = false,
)

@Serializable
data class ExpenseCreateRequest(
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
data class ExpenseUpdateRequest(
    val title: String? = null,
    val amount: Double? = null,
    val status: String? = null,
    val paidById: String? = null,
    val date: String? = null,
    val splitType: String? = null,
    val note: String? = null,
    val participants: List<ExpenseParticipantInput>? = null,
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
