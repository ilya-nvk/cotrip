package nvk.cotrip.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import nvk.cotrip.data.cache.IdeaCommentsCacheStore
import nvk.cotrip.data.cache.IdeasCacheStore
import nvk.cotrip.data.cache.ItineraryCacheStore
import nvk.cotrip.data.cache.UserCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncIdeaConvertCreatePayload
import nvk.cotrip.data.sync.SyncIdeaCreatePayload
import nvk.cotrip.data.sync.SyncIdeaStatusUpsertPayload
import nvk.cotrip.data.sync.SyncQueueRepository
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException
import java.io.IOException
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject

class IdeaRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
    private val ideasCacheStore: IdeasCacheStore,
    private val commentsCacheStore: IdeaCommentsCacheStore,
    private val itineraryCacheStore: ItineraryCacheStore,
    private val userCacheStore: UserCacheStore,
    private val networkStateProvider: NetworkStateProvider,
) : IdeaRepository {

    private companion object {
        private const val TAG = "IdeaRepository"
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeIdeas(tripId: String): Flow<List<IdeaDto>> {
        return ideasCacheStore.observeIdeas(tripId)
    }

    override fun getIdea(ideaId: String): Flow<IdeaDto> {
        if (networkStateProvider.isOnline()) {
            ioScope.launch {
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
        }
        return ideasCacheStore.observeIdeaById(ideaId).mapNotNull { it }
    }

    override fun observeComments(ideaId: String): Flow<List<CommentDto>> {
        return commentsCacheStore.observeComments(ideaId)
    }

    override suspend fun createIdea(tripId: String, request: CreateIdeaRequest): IdeaDto {
        return try {
            val idea = api.createIdea(tripId, request)
            safeLocalMutation("createIdea.upsertIdea(tripId=$tripId, ideaId=${idea.id})") {
                ideasCacheStore.upsertIdea(tripId, idea)
            }
            idea
        } catch (e: IOException) {
            val localIdea = IdeaDto(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                authorId = userCacheStore.getUser()?.id ?: "",
                title = request.title,
                city = request.city,
                link = request.link,
                costAmount = request.costAmount,
                costType = request.costType,
                notes = request.notes,
                status = "pending",
                updatedAt = OffsetDateTime.now().toString(),
                commentsCount = 0,
            )
            safeLocalMutation("createIdea.offlineUpsert(ideaId=${localIdea.id})") {
                ideasCacheStore.upsertIdea(tripId, localIdea)
            }
            syncQueueRepository.enqueueCreate(
                entity = SyncEntities.IDEA,
                id = localIdea.id,
                payload = SyncIdeaCreatePayload(
                    tripId = tripId,
                    title = request.title,
                    city = request.city,
                    link = request.link,
                    costAmount = request.costAmount,
                    costType = request.costType,
                    notes = request.notes,
                ),
            )
            localIdea
        }
    }

    override suspend fun updateIdea(ideaId: String, request: UpdateIdeaRequest) {
        val updated = try {
            api.updateIdea(ideaId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.IDEA, ideaId, request)
            applyIdeaUpdateLocally(ideaId, request)
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

    override suspend fun deleteComment(commentId: String) {
        api.deleteComment(commentId).requireSuccess()
    }

    override suspend fun convertIdeaToActivity(ideaId: String, request: ConvertIdeaRequest) {
        try {
            api.convertIdeaToActivity(ideaId, request).requireSuccess()
        } catch (e: IOException) {
            syncQueueRepository.enqueueCreate(
                entity = SyncEntities.IDEA_CONVERT,
                id = ideaId,
                payload = SyncIdeaConvertCreatePayload(
                    dayId = request.dayId,
                    timeText = request.timeText,
                    orderIndex = request.orderIndex,
                ),
            )
            applyIdeaConvertLocally(ideaId = ideaId, request = request)
            return
        }
    }

    override suspend fun approveIdea(ideaId: String): IdeaDto {
        val idea = try {
            api.approveIdea(ideaId)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(
                entity = SyncEntities.IDEA_STATUS,
                id = ideaId,
                payload = SyncIdeaStatusUpsertPayload(status = "approved"),
            )
            applyIdeaStatusLocally(ideaId = ideaId, status = "approved") ?: throw e
        }
        safeLocalMutation("approveIdea.upsertIdea(ideaId=$ideaId)") {
            ideasCacheStore.upsertIdea(idea.tripId, idea)
        }
        return idea
    }

    override suspend fun rejectIdea(ideaId: String): IdeaDto {
        val idea = try {
            api.rejectIdea(ideaId)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(
                entity = SyncEntities.IDEA_STATUS,
                id = ideaId,
                payload = SyncIdeaStatusUpsertPayload(status = "rejected"),
            )
            applyIdeaStatusLocally(ideaId = ideaId, status = "rejected") ?: throw e
        }
        safeLocalMutation("rejectIdea.upsertIdea(ideaId=$ideaId)") {
            ideasCacheStore.upsertIdea(idea.tripId, idea)
        }
        return idea
    }

    override suspend fun refreshIdeas(tripId: String): Result<Unit> {
        return runCatching {
            val ideas = mutableListOf<IdeaDto>()
            var cursor: String? = null
            do {
                val page = api.listIdeas(tripId = tripId, limit = 100, cursor = cursor)
                ideas += page.items
                cursor = page.nextCursor
            } while (cursor != null)
            safeLocalMutation("refreshIdeas.setIdeas(tripId=$tripId)") {
                ideasCacheStore.setIdeas(tripId, ideas)
            }
        }
    }

    override suspend fun refreshComments(ideaId: String): Result<Unit> {
        return runCatching {
            val comments = mutableListOf<CommentDto>()
            var cursor: String? = null
            do {
                val page = api.listComments(ideaId = ideaId, limit = 50, cursor = cursor)
                comments += page.items
                cursor = page.nextCursor
            } while (cursor != null)
            safeLocalMutation("refreshComments.setComments(ideaId=$ideaId)") {
                commentsCacheStore.setComments(ideaId, comments)
            }
        }
    }

    private suspend fun applyIdeaUpdateLocally(
        ideaId: String,
        request: UpdateIdeaRequest,
    ) {
        val local = runCatching { ideasCacheStore.findIdeaById(ideaId) }.getOrNull() ?: return
        val updated = local.copy(
            title = request.title ?: local.title,
            city = request.city ?: local.city,
            link = request.link ?: local.link,
            costAmount = request.costAmount ?: local.costAmount,
            costType = request.costType ?: local.costType,
            notes = request.notes ?: local.notes,
            updatedAt = OffsetDateTime.now().toString(),
        )
        safeLocalMutation("updateIdea.offlineUpsert(ideaId=$ideaId)") {
            ideasCacheStore.upsertIdea(updated.tripId, updated)
        }
    }

    private suspend fun applyIdeaStatusLocally(
        ideaId: String,
        status: String,
    ): IdeaDto? {
        val local = runCatching { ideasCacheStore.findIdeaById(ideaId) }.getOrNull() ?: return null
        val updated = local.copy(
            status = status,
            updatedAt = OffsetDateTime.now().toString(),
        )
        safeLocalMutation("ideaStatus.offlineUpsert(ideaId=$ideaId,status=$status)") {
            ideasCacheStore.upsertIdea(updated.tripId, updated)
        }
        return updated
    }

    private suspend fun applyIdeaConvertLocally(
        ideaId: String,
        request: ConvertIdeaRequest,
    ) {
        val sourceIdea = runCatching { ideasCacheStore.findIdeaById(ideaId) }.getOrNull() ?: return
        val itineraryByTrip = runCatching { itineraryCacheStore.getAll() }.getOrDefault(emptyMap())
        val tripId = itineraryByTrip.entries.firstOrNull { (_, days) ->
            days.any { it.id == request.dayId }
        }?.key ?: return

        safeLocalMutation("convertIdea.offlineCreateActivity(ideaId=$ideaId,dayId=${request.dayId})") {
            itineraryCacheStore.updateItinerary(tripId) { days ->
                days.map { day ->
                    if (day.id != request.dayId) {
                        day
                    } else {
                        val orderIndex = request.orderIndex
                            ?: ((day.activities.maxOfOrNull { it.orderIndex } ?: -1) + 1)
                        val converted = ActivityDto(
                            id = UUID.randomUUID().toString(),
                            dayId = day.id,
                            sourceIdeaId = sourceIdea.id,
                            title = sourceIdea.title,
                            timeText = request.timeText,
                            locationName = sourceIdea.city,
                            link = sourceIdea.link,
                            costAmount = sourceIdea.costAmount,
                            costType = sourceIdea.costType,
                            notes = sourceIdea.notes,
                            orderIndex = orderIndex,
                        )
                        day.copy(
                            activities = (day.activities + converted).sortedBy { it.orderIndex }
                        )
                    }
                }
            }
        }
    }
}
