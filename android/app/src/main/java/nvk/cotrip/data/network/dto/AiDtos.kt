package nvk.cotrip.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AiSuggestionsRequestDto(
    val city: String? = null,
    val description: String? = null,
    val typeOptions: List<String> = emptyList(),
    val timeOfDayOptions: List<String> = emptyList(),
    val budgetOptions: List<String> = emptyList(),
)

@Serializable
data class AiSuggestionDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val typeLabel: String? = null,
    val durationLabel: String? = null,
    val budgetLabel: String? = null,
    val estimatedCost: Double? = null,
    val isSaved: Boolean = false,
)

@Serializable
data class AiSuggestionsResponseDto(
    val items: List<AiSuggestionDto> = emptyList(),
)
