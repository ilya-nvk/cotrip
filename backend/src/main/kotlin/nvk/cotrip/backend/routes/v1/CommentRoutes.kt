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
import nvk.cotrip.backend.db.CommentRepository
import nvk.cotrip.backend.db.IdeaRepository
import nvk.cotrip.backend.db.TripRepository

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

            val comments = CommentRepository.listByIdea(ideaId).map {
                CommentDto(
                    id = it.id,
                    ideaId = it.ideaId,
                    authorId = it.authorId,
                    type = it.type,
                    body = it.body,
                    createdAt = it.createdAt.toString(),
                )
            }

            call.respond(mapOf("items" to comments, "nextCursor" to null))
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
