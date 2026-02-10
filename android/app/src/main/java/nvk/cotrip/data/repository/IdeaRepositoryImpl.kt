package nvk.cotrip.data.repository

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.cache.IdeasCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException

class IdeaRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
    private val ideasCacheStore: IdeasCacheStore,
) : IdeaRepository {

    private companion object {
        private const val TAG = "IdeaRepository"
    }

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
        safeLocalMutation("createIdea.upsertIdea(tripId=$tripId, ideaId=${idea.id})") {
            ideasCacheStore.upsertIdea(tripId, idea)
        }
        return idea
    }

    override suspend fun updateIdea(ideaId: String, request: UpdateIdeaRequest) {
        val updated = try {
            api.updateIdea(ideaId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.IDEA, ideaId, request)
            return
        }
        safeLocalMutation("updateIdea.upsertIdea(ideaId=$ideaId)") {
            ideasCacheStore.upsertIdea(updated.tripId, updated)
        }
    }

    override suspend fun deleteIdea(ideaId: String) {
        val ideaTripId = runCatching { api.getIdea(ideaId).tripId }
            .onFailure { AppLogger.w(TAG, "deleteIdea prefetch failed for ideaId=$ideaId", it) }
            .getOrNull()
        try {
            api.deleteIdea(ideaId).requireSuccess()
        } catch (e: IOException) {
            syncQueueRepository.enqueueDelete(SyncEntities.IDEA, ideaId)
            return
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            AppLogger.i(TAG, "deleteIdea got 404 for ideaId=$ideaId, treating as already deleted")
        }
        if (ideaTripId != null) {
            safeLocalMutation("deleteIdea.removeIdea(ideaId=$ideaId)") {
                ideasCacheStore.removeIdea(ideaTripId, ideaId)
            }
        }
    }

    override suspend fun convertIdeaToActivity(ideaId: String, request: ConvertIdeaRequest) {
        api.convertIdeaToActivity(ideaId, request).requireSuccess()
    }

    override suspend fun approveIdea(ideaId: String): IdeaDto {
        val idea = api.approveIdea(ideaId)
        safeLocalMutation("approveIdea.upsertIdea(ideaId=$ideaId)") {
            ideasCacheStore.upsertIdea(idea.tripId, idea)
        }
        return idea
    }

    override suspend fun rejectIdea(ideaId: String): IdeaDto {
        val idea = api.rejectIdea(ideaId)
        safeLocalMutation("rejectIdea.upsertIdea(ideaId=$ideaId)") {
            ideasCacheStore.upsertIdea(idea.tripId, idea)
        }
        return idea
    }

    override suspend fun refreshIdeas(tripId: String): List<IdeaDto> {
        val ideas = api.listIdeas(tripId).items
        safeLocalMutation("refreshIdeas.setIdeas(tripId=$tripId)") {
            ideasCacheStore.setIdeas(tripId, ideas)
        }
        return ideas
    }

    override suspend fun refreshComments(ideaId: String): List<CommentDto> = listComments(ideaId)
}
