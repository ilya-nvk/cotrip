package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.UpdateIdeaRequest

interface IdeaRepository {
    fun observeIdeas(tripId: String): Flow<List<IdeaDto>>
    fun getIdea(ideaId: String): Flow<IdeaDto>
    fun observeComments(ideaId: String): Flow<List<CommentDto>>
    suspend fun createIdea(tripId: String, request: CreateIdeaRequest): IdeaDto
    suspend fun updateIdea(ideaId: String, request: UpdateIdeaRequest)
    suspend fun deleteIdea(ideaId: String)
    suspend fun convertIdeaToActivity(ideaId: String, request: ConvertIdeaRequest)
    suspend fun approveIdea(ideaId: String): IdeaDto
    suspend fun rejectIdea(ideaId: String): IdeaDto
    suspend fun refreshIdeas(tripId: String): Result<Unit>
    suspend fun refreshComments(ideaId: String): Result<Unit>
}
