package nvk.cotrip.data.cache

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.CommentDto

interface IdeaCommentsCacheStore {
    fun observeComments(ideaId: String): Flow<List<CommentDto>>
    suspend fun getComments(ideaId: String): List<CommentDto>
    suspend fun setComments(ideaId: String, comments: List<CommentDto>)
    suspend fun clearIdea(ideaId: String)
    suspend fun clearAll()
}
