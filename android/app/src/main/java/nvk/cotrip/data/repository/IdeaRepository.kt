package nvk.cotrip.data.repository

import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import kotlinx.coroutines.flow.Flow

interface IdeaRepository {
    fun observeIdeas(tripId: String): Flow<List<IdeaDto>>
    suspend fun listIdeas(tripId: String): List<IdeaDto>
    suspend fun getIdea(ideaId: String): IdeaDto
    suspend fun listComments(ideaId: String): List<CommentDto>
    suspend fun createIdea(tripId: String, request: CreateIdeaRequest): IdeaDto
    suspend fun updateIdea(ideaId: String, request: UpdateIdeaRequest)
    suspend fun deleteIdea(ideaId: String)
    suspend fun convertIdeaToActivity(ideaId: String, request: ConvertIdeaRequest)
    suspend fun approveIdea(ideaId: String): IdeaDto
    suspend fun rejectIdea(ideaId: String): IdeaDto
    suspend fun refreshIdeas(tripId: String): List<IdeaDto>
    suspend fun refreshComments(ideaId: String): List<CommentDto>
}
