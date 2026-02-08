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
import nvk.cotrip.backend.db.DayRepository
import nvk.cotrip.backend.db.IdeaRepository
import nvk.cotrip.backend.db.IdeaRow
import nvk.cotrip.backend.db.TripRepository

@Serializable
data class CreateIdeaRequest(
    val title: String,
    val city: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val website: String? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateIdeaRequest(
    val title: String? = null,
    val city: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val website: String? = null,
    val notes: String? = null,
)

@Serializable
data class ConvertToActivityRequest(
    val dayId: String,
    val timeText: String? = null,
    val orderIndex: Int? = null,
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

            val ideas = IdeaRepository.list(tripId, search, status, authorId, city).map { idea ->
                idea.toDto()
            }

            call.respond(mapOf("items" to ideas, "nextCursor" to null))
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
                costAmount = request.costAmount,
                costType = request.costType,
                website = request.website,
                notes = request.notes,
            )

            call.respond(idea.toDto())
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

            call.respond(idea.toDto())
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
                costAmount = request.costAmount,
                costType = request.costType,
                website = request.website,
                notes = request.notes,
            )

            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            call.respond(updated.toDto())
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

            call.respond(updated.toDto())
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

            call.respond(updated.toDto())
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

            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun IdeaRow.toDto(): IdeaDto = IdeaDto(
    id = id,
    tripId = tripId,
    authorId = authorId,
    title = title,
    city = city,
    costAmount = costAmount,
    costType = costType,
    website = website,
    notes = notes,
    status = status,
    updatedAt = updatedAt.toString(),
)
