package nvk.cotrip.backend.routes.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import nvk.cotrip.backend.db.ActivityRepository
import nvk.cotrip.backend.db.CommentRepository
import nvk.cotrip.backend.db.DayRepository
import nvk.cotrip.backend.db.IdeaRepository
import nvk.cotrip.backend.db.IdeaRow
import nvk.cotrip.backend.db.TripRepository
import nvk.cotrip.backend.db.UserRepository
import nvk.cotrip.backend.notifications.NotificationService
import nvk.cotrip.backend.ws.publishCommentCreated

@Serializable
data class CreateIdeaRequest(
    val title: String,
    val city: String? = null,
    val link: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateIdeaRequest(
    val title: String? = null,
    val city: String? = null,
    val link: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val notes: String? = null,
)

@Serializable
data class ConvertToActivityRequest(
    val dayId: String,
    val timeText: String? = null,
    val orderIndex: Int? = null,
)

@Serializable
private data class IdeaListResponse(
    val items: List<IdeaDto>,
    val nextCursor: String? = null,
)

fun Route.ideaRoutes() {
    authenticate("auth-jwt") {
        get("/v1/trips/{tripId}/ideas") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val tripId = call.parameters["tripId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            if (!TripRepository.isMember(tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

            val search = call.request.queryParameters["search"]
            val status = call.request.queryParameters["status"]
            val authorId = call.request.queryParameters["authorId"]
            val city = call.request.queryParameters["city"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100)
            val cursor = call.request.queryParameters["cursor"]

            if (limit == null && cursor.isNullOrBlank()) {
                val ideas = IdeaRepository.list(tripId, search, status, authorId, city)
                val commentCounts = CommentRepository.countByIdeaIds(ideas.map { it.id })
                val historyIds = CommentRepository.ideaIdsWithUserCommentHistory(ideas.map { it.id })
                val items = ideas.map { idea ->
                    idea.toDto(
                        commentsCount = commentCounts[idea.id] ?: 0,
                        hasHumanCommentHistory = historyIds.contains(idea.id),
                    )
                }
                call.respond(IdeaListResponse(items = items, nextCursor = null))
            } else {
                val page = IdeaRepository.listPage(
                    tripId = tripId,
                    search = search,
                    status = status,
                    authorId = authorId,
                    city = city,
                    limit = limit ?: 100,
                    cursor = cursor,
                )
                val commentCounts = CommentRepository.countByIdeaIds(page.items.map { it.id })
                val historyIds = CommentRepository.ideaIdsWithUserCommentHistory(page.items.map { it.id })
                val items = page.items.map { idea ->
                    idea.toDto(
                        commentsCount = commentCounts[idea.id] ?: 0,
                        hasHumanCommentHistory = historyIds.contains(idea.id),
                    )
                }
                call.respond(
                    IdeaListResponse(
                        items = items,
                        nextCursor = page.nextCursor,
                    )
                )
            }
        }

        post("/v1/trips/{tripId}/ideas") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val tripId = call.parameters["tripId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            if (!TripRepository.isMember(tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val request = call.receive<CreateIdeaRequest>()
            val idea = IdeaRepository.create(
                tripId = tripId,
                authorId = userId,
                title = request.title,
                city = request.city,
                link = request.link,
                costAmount = request.costAmount,
                costType = request.costType,
                notes = request.notes,
            )

            val actorName = UserRepository.findById(userId)?.name.orEmpty()
            NotificationService.notifyIdeaCreated(
                tripId = tripId,
                ideaId = idea.id,
                actorUserId = userId,
                actorName = actorName,
                ideaTitle = idea.title
            )

            call.respond(idea.toDto(commentsCount = 0, hasHumanCommentHistory = false))
        }

        get("/v1/ideas/{ideaId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val ideaId = call.parameters["ideaId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val idea = IdeaRepository.get(ideaId)
            if (idea == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            if (!TripRepository.isMember(idea.tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@get
            }

            val commentCount = CommentRepository.countByIdeaIds(listOf(idea.id))[idea.id] ?: 0
            val hasHumanCommentHistory =
                CommentRepository.ideaIdsWithUserCommentHistory(listOf(idea.id)).contains(idea.id)
            call.respond(idea.toDto(commentCount, hasHumanCommentHistory = hasHumanCommentHistory))
        }

        patch("/v1/ideas/{ideaId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@patch
            }

            val ideaId = call.parameters["ideaId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@patch
            }

            val existing = IdeaRepository.get(ideaId)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            val isOwner = TripRepository.isOwner(existing.tripId, userId)
            if (!isOwner && existing.authorId != userId) {
                call.respond(HttpStatusCode.Forbidden)
                return@patch
            }

            val request = call.receive<UpdateIdeaRequest>()
            val updated = IdeaRepository.update(
                ideaId = ideaId,
                title = request.title,
                city = request.city,
                link = request.link,
                costAmount = request.costAmount,
                costType = request.costType,
                notes = request.notes,
            )

            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            if (ideaChanged(existing, updated)) {
                val actorName = UserRepository.findById(userId)?.name.orEmpty()
                val systemComment = CommentRepository.createSystem(
                    ideaId = updated.id,
                    authorId = userId,
                    body = "$actorName edited the idea"
                )
                publishCommentCreated(updated.tripId, systemComment)
                NotificationService.notifyIdeaComment(
                    tripId = updated.tripId,
                    ideaId = updated.id,
                    actorUserId = userId,
                    actorName = actorName,
                    body = systemComment.body
                )
            }

            val commentCount = CommentRepository.countByIdeaIds(listOf(updated.id))[updated.id] ?: 0
            val hasHumanCommentHistory =
                CommentRepository.ideaIdsWithUserCommentHistory(listOf(updated.id)).contains(updated.id)
            call.respond(updated.toDto(commentCount, hasHumanCommentHistory = hasHumanCommentHistory))
        }

        delete("/v1/ideas/{ideaId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@delete
            }

            val ideaId = call.parameters["ideaId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            val existing = IdeaRepository.get(ideaId)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            val isOwner = TripRepository.isOwner(existing.tripId, userId)
            if (!isOwner && existing.authorId != userId) {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }

            val deleted = IdeaRepository.softDelete(ideaId)
            if (!deleted) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            call.respond(HttpStatusCode.NoContent)
        }

        post("/v1/ideas/{ideaId}/approve") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val ideaId = call.parameters["ideaId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val existing = IdeaRepository.get(ideaId)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            if (!TripRepository.isOwner(existing.tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val updated = IdeaRepository.updateStatus(ideaId, "approved")
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val commentCount = CommentRepository.countByIdeaIds(listOf(updated.id))[updated.id] ?: 0
            val hasHumanCommentHistory =
                CommentRepository.ideaIdsWithUserCommentHistory(listOf(updated.id)).contains(updated.id)
            call.respond(updated.toDto(commentCount, hasHumanCommentHistory = hasHumanCommentHistory))
        }

        post("/v1/ideas/{ideaId}/reject") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val ideaId = call.parameters["ideaId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val existing = IdeaRepository.get(ideaId)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            if (!TripRepository.isOwner(existing.tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val updated = IdeaRepository.updateStatus(ideaId, "rejected")
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val commentCount = CommentRepository.countByIdeaIds(listOf(updated.id))[updated.id] ?: 0
            val hasHumanCommentHistory =
                CommentRepository.ideaIdsWithUserCommentHistory(listOf(updated.id)).contains(updated.id)
            call.respond(updated.toDto(commentCount, hasHumanCommentHistory = hasHumanCommentHistory))
        }

        post("/v1/ideas/{ideaId}/convert-to-activity") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val ideaId = call.parameters["ideaId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val request = call.receive<ConvertToActivityRequest>()
            val dayTripId = DayRepository.findTripIdByDayId(request.dayId)
            if (dayTripId == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val idea = IdeaRepository.get(ideaId)
            if (idea == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            if (idea.tripId != dayTripId) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to mapOf("code" to "invalid_day", "message" to "Day does not belong to idea trip")))
                return@post
            }

            if (!TripRepository.isMember(idea.tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val orderIndex = request.orderIndex ?: ActivityRepository.nextOrderIndex(request.dayId)
            ActivityRepository.createFromIdea(
                dayId = request.dayId,
                idea = idea,
                timeText = request.timeText,
                orderIndex = orderIndex,
            )

            val actorName = UserRepository.findById(userId)?.name.orEmpty()
            val systemComment = CommentRepository.createSystem(
                ideaId = idea.id,
                authorId = userId,
                body = "$actorName added this idea to the itinerary"
            )
            publishCommentCreated(idea.tripId, systemComment)
            NotificationService.notifyIdeaComment(
                tripId = idea.tripId,
                ideaId = idea.id,
                actorUserId = userId,
                actorName = actorName,
                body = systemComment.body
            )

            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ideaChanged(before: IdeaRow, after: IdeaRow): Boolean {
    return before.title != after.title ||
        before.city != after.city ||
        before.link != after.link ||
        before.costAmount != after.costAmount ||
        before.costType != after.costType ||
        before.notes != after.notes ||
        before.status != after.status
}

private fun IdeaRow.toDto(
    commentsCount: Int = 0,
    hasHumanCommentHistory: Boolean? = null,
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
    updatedAt = updatedAt.toString(),
    commentsCount = commentsCount,
    hasHumanCommentHistory = hasHumanCommentHistory,
)
