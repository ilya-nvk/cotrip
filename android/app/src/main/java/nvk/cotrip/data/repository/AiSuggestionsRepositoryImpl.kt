package nvk.cotrip.data.repository

import javax.inject.Inject
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.AiSuggestionDto
import nvk.cotrip.data.network.dto.AiSuggestionsRequestDto

class AiSuggestionsRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
) : AiSuggestionsRepository {
    override suspend fun generateSuggestions(
        tripId: String,
        request: AiSuggestionsRequestDto,
    ): List<AiSuggestionDto> {
        return api.generateAiSuggestions(tripId = tripId, request = request).items
    }

    override suspend fun saveSuggestionToIdeas(suggestionId: String) {
        api.saveAiSuggestionToIdeas(suggestionId)
    }
}
