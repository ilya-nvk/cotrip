package nvk.cotrip.data.cache

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.IdeaDto

interface IdeasCacheStore {
    fun observeIdeas(tripId: String): Flow<List<IdeaDto>>
    suspend fun getIdeas(tripId: String): List<IdeaDto>
    suspend fun findIdeaById(ideaId: String): IdeaDto?
    suspend fun setIdeas(tripId: String, ideas: List<IdeaDto>)
    suspend fun upsertIdea(tripId: String, idea: IdeaDto)
    suspend fun removeIdea(tripId: String, ideaId: String)
    suspend fun clearTrip(tripId: String)
    suspend fun clearAll()
}
