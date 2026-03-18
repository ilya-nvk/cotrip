package nvk.cotrip.ui.aisuggestions

import nvk.cotrip.data.network.dto.AiSuggestionsRequestDto
import nvk.cotrip.data.network.dto.AiSuggestionDto
import nvk.cotrip.data.repository.AiSuggestionsRepository

internal class FakeAiSuggestionsRepository(
    private val suggestions: List<AiSuggestionDto> = emptyList(),
) : AiSuggestionsRepository {

    data class GenerateCall(val tripId: String, val request: AiSuggestionsRequestDto)
    val generateSuggestionsCalls = mutableListOf<GenerateCall>()
    var generateSuggestionsError: Throwable? = null

    var saveSuggestionToIdeasCalls = mutableListOf<String>()

    override suspend fun generateSuggestions(
        tripId: String,
        request: AiSuggestionsRequestDto,
    ): List<AiSuggestionDto> {
        generateSuggestionsCalls.add(GenerateCall(tripId, request))
        generateSuggestionsError?.let { throw it }
        return suggestions
    }

    override suspend fun saveSuggestionToIdeas(suggestionId: String) {
        saveSuggestionToIdeasCalls.add(suggestionId)
    }
}

internal fun aiSuggestionDto(
    id: String = "suggestion-1",
    title: String = "Suggestion",
    place: String? = null,
    description: String? = null,
    typeLabel: String? = null,
    durationLabel: String? = null,
    budgetLabel: String? = null,
    estimatedCost: Double? = null,
    isSaved: Boolean = false,
): AiSuggestionDto = AiSuggestionDto(
    id = id,
    title = title,
    place = place,
    description = description,
    typeLabel = typeLabel,
    durationLabel = durationLabel,
    budgetLabel = budgetLabel,
    estimatedCost = estimatedCost,
    isSaved = isSaved,
)
