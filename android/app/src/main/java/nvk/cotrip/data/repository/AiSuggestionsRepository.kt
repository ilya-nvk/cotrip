package nvk.cotrip.data.repository

import nvk.cotrip.data.network.dto.AiSuggestionDto
import nvk.cotrip.data.network.dto.AiSuggestionsRequestDto

interface AiSuggestionsRepository {
    suspend fun generateSuggestions(
        tripId: String,
        request: AiSuggestionsRequestDto,
    ): List<AiSuggestionDto>

    suspend fun saveSuggestionToIdeas(suggestionId: String)
}
