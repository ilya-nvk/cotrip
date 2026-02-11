package nvk.cotrip.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nvk.cotrip.data.cache.IdeaCommentsCacheStore
import nvk.cotrip.data.cache.IdeasCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class IdeaRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
    private val ideasCacheStore: IdeasCacheStore,
    private val commentsCacheStore: IdeaCommentsCacheStore,
    private val networkStateProvider: NetworkStateProvider,
) : IdeaRepository {

    private companion object {
        private const val TAG = "IdeaRepository"
    }

    override fun observeIdeas(tripId: String): Flow<List<IdeaDto>> {
        return ideasCacheStore.observeIdeas(tripId)
    }

    override suspend fun getIdea(ideaId: String): Flow<IdeaDto> {
        if (networkStateProvider.isOnline()) {
            runCatching { api.getIdea(ideaId) }
                .onSuccess { idea ->
                    safeLocalMutation("getIdea.upsertIdea(ideaId=$ideaId)") {
                        ideasCacheStore.upsertIdea(idea.tripId, idea)
                    }
                }
                .onFailure { error ->
                    AppLogger.w(TAG, "getIdea network fetch failed for ideaId=$ideaId", error)
                }
        }
        return ideasCacheStore.observeIdeaById(ideaId).map { cached ->
            cached ?: throw IOException("Idea $ideaId is not available in cache")
        }
    }

    override fun observeComments(ideaId: String): Flow<List<CommentDto>> {
        return commentsCacheStore.observeComments(ideaId)
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
        val cachedTripId = runCatching { ideasCacheStore.findIdeaById(ideaId)?.tripId }.getOrNull()
        val ideaTripId = cachedTripId ?: runCatching { api.getIdea(ideaId).tripId }
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
        safeLocalMutation("deleteIdea.clearComments(ideaId=$ideaId)") {
            commentsCacheStore.clearIdea(ideaId)
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

    override suspend fun refreshIdeas(tripId: String): Result<Unit> {
        return runCatching {
            val ideas = api.listIdeas(tripId).items
            safeLocalMutation("refreshIdeas.setIdeas(tripId=$tripId)") {
                ideasCacheStore.setIdeas(tripId, ideas)
            }
        }
    }

    override suspend fun refreshComments(ideaId: String): Result<Unit> {
        return runCatching {
            val comments = api.listComments(ideaId).items
            safeLocalMutation("refreshComments.setComments(ideaId=$ideaId)") {
                commentsCacheStore.setComments(ideaId, comments)
            }
        }
    }
}
