package nvk.cotrip.backend.routes.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import nvk.cotrip.backend.comments.SystemCommentMetadataResolver
import nvk.cotrip.backend.db.CommentRepository
import nvk.cotrip.backend.db.IdeaRepository
import nvk.cotrip.backend.db.TripRepository
import nvk.cotrip.backend.db.UserRepository

@Serializable
private data class CommentListResponse(
    val items: List<CommentDto>,
    val nextCursor: String? = null,
)

fun Route.commentRoutes() {
    authenticate("auth-jwt") {
        get("/v1/ideas/{ideaId}/comments") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val ideaId = call.parameters["ideaId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val tripId = IdeaRepository.findTripIdByIdeaId(ideaId)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            if (!TripRepository.isMember(tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 50)
            val cursor = call.request.queryParameters["cursor"]
            val pageRows = if (limit == null && cursor.isNullOrBlank()) {
                nvk.cotrip.backend.db.CommentPage(
                    items = CommentRepository.listByIdea(ideaId),
                    nextCursor = null,
                )
            } else {
                CommentRepository.listByIdeaPage(
                    ideaId = ideaId,
                    limit = limit ?: 50,
                    cursor = cursor,
                )
            }
            val commentRows = pageRows.items
            val authorNamesById = commentRows
                .asSequence()
                .map { it.authorId }
                .distinct()
                .associateWith { authorId -> UserRepository.findById(authorId)?.name }

            val comments = commentRows.map {
                val meta = SystemCommentMetadataResolver.resolve(it.type, it.body)
                CommentDto(
                    id = it.id,
                    ideaId = it.ideaId,
                    authorId = it.authorId,
                    authorName = authorNamesById[it.authorId],
                    type = it.type,
                    body = it.body,
                    createdAt = it.createdAt.toString(),
                    systemKey = meta.systemKey,
                    systemActorName = meta.systemActorName,
                )
            }

            call.respond(CommentListResponse(items = comments, nextCursor = pageRows.nextCursor))
        }

        delete("/v1/comments/{commentId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@delete
            }

            val commentId = call.parameters["commentId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            val deleted = CommentRepository.softDelete(commentId, userId)
            if (!deleted) {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
