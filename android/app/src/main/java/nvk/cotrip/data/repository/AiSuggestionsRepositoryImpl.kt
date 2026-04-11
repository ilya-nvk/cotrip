package nvk.cotrip.data.repository

import javax.inject.Inject
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.AiSuggestionDto
import nvk.cotrip.data.network.dto.AiSuggestionsRequestDto
import nvk.cotrip.data.sync.SyncAiSuggestionSaveUpsertPayload
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import java.io.IOException

class AiSuggestionsRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
) : AiSuggestionsRepository {
    override suspend fun generateSuggestions(
        tripId: String,
        request: AiSuggestionsRequestDto,
    ): List<AiSuggestionDto> {
        return api.generateAiSuggestions(tripId = tripId, request = request).items
    }

    override suspend fun saveSuggestionToIdeas(suggestionId: String) {
        try {
            api.saveAiSuggestionToIdeas(suggestionId)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(
                entity = SyncEntities.AI_SUGGESTION_SAVE,
                id = suggestionId,
                payload = SyncAiSuggestionSaveUpsertPayload(suggestionId = suggestionId),
            )
        }
    }
}
