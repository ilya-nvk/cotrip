package nvk.cotrip.data.repository

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.cache.IdeasCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository

class IdeaRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
    private val ideasCacheStore: IdeasCacheStore,
) : IdeaRepository {
    override fun observeIdeas(tripId: String): Flow<List<IdeaDto>> {
        return ideasCacheStore.observeIdeas(tripId)
    }

    override suspend fun listIdeas(tripId: String): List<IdeaDto> {
        val cached = ideasCacheStore.getIdeas(tripId)
        if (cached.isNotEmpty()) return cached
        return refreshIdeas(tripId)
    }

    override suspend fun getIdea(ideaId: String): IdeaDto {
        return api.getIdea(ideaId)
    }

    override suspend fun listComments(ideaId: String): List<CommentDto> {
        return api.listComments(ideaId).items
    }

    override suspend fun createIdea(tripId: String, request: CreateIdeaRequest): IdeaDto {
        val idea = api.createIdea(tripId, request)
        ideasCacheStore.upsertIdea(tripId, idea)
        return idea
    }

    override suspend fun updateIdea(ideaId: String, request: UpdateIdeaRequest) {
        try {
            val updated = api.updateIdea(ideaId, request)
            ideasCacheStore.upsertIdea(updated.tripId, updated)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.IDEA, ideaId, request)
        }
    }

    override suspend fun deleteIdea(ideaId: String) {
        try {
            val idea = api.getIdea(ideaId)
            api.deleteIdea(ideaId)
            ideasCacheStore.removeIdea(idea.tripId, ideaId)
        } catch (e: IOException) {
            syncQueueRepository.enqueueDelete(SyncEntities.IDEA, ideaId)
        }
    }

    override suspend fun convertIdeaToActivity(ideaId: String, request: ConvertIdeaRequest) {
        api.convertIdeaToActivity(ideaId, request)
    }

    override suspend fun approveIdea(ideaId: String): IdeaDto {
        val idea = api.approveIdea(ideaId)
        ideasCacheStore.upsertIdea(idea.tripId, idea)
        return idea
    }

    override suspend fun rejectIdea(ideaId: String): IdeaDto {
        val idea = api.rejectIdea(ideaId)
        ideasCacheStore.upsertIdea(idea.tripId, idea)
        return idea
    }

    override suspend fun refreshIdeas(tripId: String): List<IdeaDto> {
        val ideas = api.listIdeas(tripId).items
        ideasCacheStore.setIdeas(tripId, ideas)
        return ideas
    }

    override suspend fun refreshComments(ideaId: String): List<CommentDto> = listComments(ideaId)
}
