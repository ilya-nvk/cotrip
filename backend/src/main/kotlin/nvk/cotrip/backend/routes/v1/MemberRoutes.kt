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
import nvk.cotrip.backend.db.TripMemberRepository
import nvk.cotrip.backend.db.TripRepository

fun Route.memberRoutes() {
    authenticate("auth-jwt") {
        get("/v1/trips/{tripId}/members") {
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

            val members = TripMemberRepository.listMembers(tripId).map { member ->
                MemberDto(
                    userId = member.userId,
                    name = member.name,
                    photoUrl = member.photoUrl,
                    initials = initialsFromName(member.name),
                    role = member.role,
                    status = member.status,
                )
            }

            call.respond(mapOf("items" to members))
        }

        delete("/v1/trips/{tripId}/members/{memberId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@delete
            }

            val tripId = call.parameters["tripId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            val memberId = call.parameters["memberId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            if (!TripRepository.isMember(tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }

            val target = TripMemberRepository.findMember(tripId, memberId)
            if (target == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            if (target.role == "owner") {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }

            val isOwner = TripRepository.isOwner(tripId, userId)
            if (!isOwner && userId != memberId) {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }

            val removed = TripMemberRepository.removeMember(tripId, memberId)
            if (!removed) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
