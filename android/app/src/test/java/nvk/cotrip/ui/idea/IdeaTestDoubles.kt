package nvk.cotrip.ui.idea

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.ws.CommentEventsSource
import nvk.cotrip.data.network.ws.CommentEventsSourceFactory
import nvk.cotrip.data.network.ws.CommentWsEvent
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import nvk.cotrip.data.repository.IdeaRepository

internal class FakeIdeaRepository(
    initialIdea: IdeaDto? = null,
) : IdeaRepository {
    private val ideaFlow = MutableStateFlow(initialIdea)

    var createIdeaResult: IdeaDto? = null
    var createIdeaToThrow: Throwable? = null
    val createIdeaCalls = mutableListOf<Pair<String, CreateIdeaRequest>>()
    val updateIdeaCalls = mutableListOf<Pair<String, UpdateIdeaRequest>>()
    val deleteIdeaCalls = mutableListOf<String>()
    val convertIdeaToActivityCalls = mutableListOf<Pair<String, ConvertIdeaRequest>>()

    override fun observeIdeas(tripId: String): Flow<List<IdeaDto>> =
        ideaFlow.map { listOfNotNull(it) }

    override fun getIdea(ideaId: String): Flow<IdeaDto> = kotlinx.coroutines.flow.flow {
        emit(ideaFlow.value ?: ideaDto(id = ideaId))
    }

    override fun observeComments(ideaId: String): Flow<List<CommentDto>> = flowOf(emptyList())

    override suspend fun createIdea(tripId: String, request: CreateIdeaRequest): IdeaDto {
        createIdeaToThrow?.let { throw it }
        createIdeaCalls += tripId to request
        return createIdeaResult ?: ideaDto(
            id = "idea-created",
            tripId = tripId,
            title = request.title,
        )
    }

    override suspend fun updateIdea(ideaId: String, request: UpdateIdeaRequest) {
        updateIdeaCalls += ideaId to request
    }

    override suspend fun deleteIdea(ideaId: String) {
        deleteIdeaCalls += ideaId
    }

    override suspend fun deleteComment(commentId: String) = Unit

    override suspend fun convertIdeaToActivity(ideaId: String, request: ConvertIdeaRequest) {
        convertIdeaToActivityCalls += ideaId to request
    }

    override suspend fun approveIdea(ideaId: String): IdeaDto = ideaDto(id = ideaId, status = "approved")

    override suspend fun rejectIdea(ideaId: String): IdeaDto = ideaDto(id = ideaId, status = "rejected")

    override suspend fun refreshIdeas(tripId: String): Result<Unit> = Result.success(Unit)

    override suspend fun refreshComments(ideaId: String): Result<Unit> = Result.success(Unit)

    fun setIdea(idea: IdeaDto) {
        ideaFlow.value = idea
    }
}

internal class FakeCommentEventsSource(
    override val events: Flow<CommentWsEvent> = emptyFlow(),
) : CommentEventsSource {
    override fun connect(baseUrl: String, tripId: String, token: String) {}
    override fun disconnect() {}
    override fun sendCreate(ideaId: String, body: String, clientMessageId: String?): Boolean = true
}

internal class FakeCommentEventsSourceFactory(
    private val source: FakeCommentEventsSource = FakeCommentEventsSource(),
) : CommentEventsSourceFactory {
    override fun create(): CommentEventsSource = source
}

internal fun ideaDto(
    id: String = "idea-1",
    tripId: String = "trip-1",
    authorId: String = "user-1",
    title: String = "Test Idea",
    city: String? = null,
    link: String? = null,
    costAmount: Double? = null,
    costType: String? = "per_person",
    notes: String? = null,
    status: String = "pending",
    updatedAt: String = "2026-03-17T10:00:00Z",
    commentsCount: Int = 0,
): IdeaDto = IdeaDto(
    id = id,
    tripId = tripId,
    authorId = authorId,
    title = title,
    city = city,
    link = link,
    costAmount = costAmount,
    costType = costType,
    notes = notes,
    status = status,
    updatedAt = updatedAt,
    commentsCount = commentsCount,
)
