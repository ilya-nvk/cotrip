package nvk.cotrip.data.repository

import java.io.IOException
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import javax.inject.Inject

class IdeaRepository @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
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

    suspend fun updateIdea(ideaId: String, request: UpdateIdeaRequest) {
        try {
            api.updateIdea(ideaId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.IDEA, ideaId, request)
        }
    }

    suspend fun deleteIdea(ideaId: String) {
        try {
            api.deleteIdea(ideaId)
        } catch (e: IOException) {
            syncQueueRepository.enqueueDelete(SyncEntities.IDEA, ideaId)
        }
    }

    suspend fun convertIdeaToActivity(ideaId: String, request: ConvertIdeaRequest) {
        api.convertIdeaToActivity(ideaId, request)
    }

    suspend fun approveIdea(ideaId: String): IdeaDto {
        return api.approveIdea(ideaId)
    }

    suspend fun rejectIdea(ideaId: String): IdeaDto {
        return api.rejectIdea(ideaId)
    }

    suspend fun refreshIdeas(tripId: String): List<IdeaDto> = listIdeas(tripId)

    suspend fun refreshComments(ideaId: String): List<CommentDto> = listComments(ideaId)
}
