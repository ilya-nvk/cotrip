package nvk.cotrip.backend.routes.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import nvk.cotrip.backend.config.AppConfig
import nvk.cotrip.backend.db.InviteRepository
import nvk.cotrip.backend.db.TripRepository
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

fun Route.inviteRoutes(appConfig: AppConfig) {
    authenticate("auth-jwt") {
        listOf("/v1/trips/{tripId}/invite", "/trips/{tripId}/invite").forEach { routePath ->
            post(routePath) {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class) ?: run {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val tripId = call.parameters["tripId"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
                val normalizedTripId = runCatching { UUID.fromString(tripId).toString() }.getOrElse {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to mapOf("code" to "invalid_trip_id", "message" to "tripId must be a valid UUID"))
                    )
                    return@post
                }

                if (!TripRepository.isOwner(normalizedTripId, userId)) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@post
                }

                InviteRepository.revokeActiveInvites(normalizedTripId)

                val token = UUID.randomUUID().toString().replace("-", "")
                val expiresAt = OffsetDateTime.now().plusHours(12)
                val invite = InviteRepository.createInvite(normalizedTripId, userId, token, expiresAt)

                val url = buildInviteUrl(appConfig.invite.baseUrl, invite.token)
                call.respond(
                    InviteLinkDto(
                        token = invite.token,
                        url = url,
                        expiresAt = invite.expiresAt.toString(),
                    )
                )
            }
        }
    }

    listOf("/v1/invites/{token}", "/invite/{token}").forEach { routePath ->
        get(routePath) {
            val token = call.parameters["token"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val invite = InviteRepository.findActiveByToken(token)
            if (invite == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val trip = TripRepository.getTripById(invite.tripId)
            if (trip == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.respond(
                InviteInfoDto(
                    tripId = trip.id,
                    title = trip.title,
                    startDate = trip.startDate.toString(),
                    endDate = trip.endDate.toString(),
                    locationLine = trip.locationLine,
                    expiresAt = invite.expiresAt.toString(),
                )
            )
        }
    }

    authenticate("auth-jwt") {
        listOf("/v1/invites/{token}/accept", "/invite/{token}/accept").forEach { routePath ->
            post(routePath) {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.getClaim("userId", String::class) ?: run {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                val token = call.parameters["token"] ?: run {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }

                val invite = InviteRepository.findActiveByToken(token)
                if (invite == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@post
                }

                TripRepository.upsertMemberAccepted(invite.tripId, userId, "member")
                InviteRepository.incrementUse(invite.id)

                call.respond(mapOf("tripId" to invite.tripId))
            }
        }
    }
}

private fun buildInviteUrl(baseUrl: String, token: String): String {
    val trimmedBase = baseUrl.trim()
    if (trimmedBase.contains("{token}")) {
        return trimmedBase.replace("{token}", token)
    }

    val parsed = runCatching { URI(trimmedBase) }.getOrNull()
    if (parsed?.scheme != null && parsed.authority != null) {
        val normalizedPath = when (val path = parsed.path.orEmpty().trimEnd('/')) {
            "",
            "/",
                -> "/invite"

            "/invite" -> "/invite"
            else -> if (path.startsWith("/v1", ignoreCase = true) || path.contains("/trips/", ignoreCase = true)) {
                "/invite"
            } else {
                path
            }
        }
        return "${parsed.scheme}://${parsed.authority}$normalizedPath/$token"
    }

    return "${trimmedBase.trimEnd('/')}/$token"
}
