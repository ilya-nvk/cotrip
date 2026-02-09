package nvk.cotrip.data.repository

import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import javax.inject.Inject

class IdeaRepository @Inject constructor(
    private val api: CoTripApi,
) {
    suspend fun listIdeas(tripId: String): List<IdeaDto> {
        return api.listIdeas(tripId).items
    }

    suspend fun getIdea(ideaId: String): IdeaDto {
        return api.getIdea(ideaId)
    }

    suspend fun listComments(ideaId: String): List<CommentDto> {
        return api.listComments(ideaId).items
    }

    suspend fun createIdea(tripId: String, request: CreateIdeaRequest): IdeaDto {
        return api.createIdea(tripId, request)
    }

    suspend fun updateIdea(ideaId: String, request: UpdateIdeaRequest): IdeaDto {
        return api.updateIdea(ideaId, request)
    }

    suspend fun deleteIdea(ideaId: String) {
        api.deleteIdea(ideaId)
    }

    suspend fun convertIdeaToActivity(ideaId: String, request: ConvertIdeaRequest) {
        api.convertIdeaToActivity(ideaId, request)
    }

    suspend fun refreshIdeas(tripId: String): List<IdeaDto> = listIdeas(tripId)

    suspend fun refreshComments(ideaId: String): List<CommentDto> = listComments(ideaId)
}
