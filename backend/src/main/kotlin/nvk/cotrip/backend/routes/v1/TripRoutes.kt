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
import nvk.cotrip.backend.db.TripRepository
import nvk.cotrip.backend.db.TripUpdate
import java.time.LocalDate
import java.util.UUID

@Serializable
data class CreateTripRequest(
    val title: String,
    val description: String? = null,
    val startDate: String,
    val endDate: String,
    val locationLine: String? = null,
    val coverUrl: String? = null,
    val currencyCode: String,
)

@Serializable
data class UpdateTripRequest(
    val title: String? = null,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val locationLine: String? = null,
    val coverUrl: String? = null,
    val currencyCode: String? = null,
)

@Serializable
data class TransferOwnerRequest(
    val newOwnerId: String,
)

fun Route.tripRoutes() {
    authenticate("auth-jwt") {
        post("/v1/trips") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val request = call.receive<CreateTripRequest>()
            val startDate = LocalDate.parse(request.startDate)
            val endDate = LocalDate.parse(request.endDate)
            if (endDate.isBefore(startDate)) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to mapOf("code" to "invalid_dates", "message" to "endDate must be after startDate")))
                return@post
            }

            val trip = TripRepository.createTrip(
                ownerId = userId,
                title = request.title,
                description = request.description,
                startDate = startDate,
                endDate = endDate,
                locationLine = request.locationLine,
                coverUrl = request.coverUrl,
                currencyCode = request.currencyCode,
            )

            call.respond(trip.toDto())
        }

        get("/v1/trips") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val status = call.request.queryParameters["status"]
            val trips = TripRepository.listTripsForUser(userId, status).map { it.toDto() }
            call.respond(mapOf("items" to trips, "nextCursor" to null))
        }

        get("/v1/trips/{tripId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val tripId = call.parameters["tripId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val trip = TripRepository.getTripForUser(userId, tripId)
            if (trip == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            call.respond(trip.toDto())
        }

        patch("/v1/trips/{tripId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@patch
            }

            val tripId = call.parameters["tripId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@patch
            }

            val request = call.receive<UpdateTripRequest>()
            val currentTrip = TripRepository.getTripForUser(userId, tripId) ?: run {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }
            val parsedStartDate = request.startDate?.let { raw ->
                runCatching { LocalDate.parse(raw) }.getOrElse {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to mapOf("code" to "invalid_dates", "message" to "startDate must be a valid ISO date"))
                    )
                    return@patch
                }
            }
            val parsedEndDate = request.endDate?.let { raw ->
                runCatching { LocalDate.parse(raw) }.getOrElse {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to mapOf("code" to "invalid_dates", "message" to "endDate must be a valid ISO date"))
                    )
                    return@patch
                }
            }
            val effectiveStart = parsedStartDate ?: currentTrip.startDate
            val effectiveEnd = parsedEndDate ?: currentTrip.endDate
            if (effectiveEnd.isBefore(effectiveStart)) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to mapOf("code" to "invalid_dates", "message" to "endDate must be after startDate"))
                )
                return@patch
            }

            val update = TripUpdate(
                title = request.title,
                description = request.description,
                startDate = parsedStartDate,
                endDate = parsedEndDate,
                locationLine = request.locationLine,
                coverUrl = request.coverUrl,
                currencyCode = request.currencyCode,
            )

            val trip = TripRepository.updateTrip(userId, tripId, update)
            if (trip == null) {
                call.respond(HttpStatusCode.Forbidden)
                return@patch
            }

            call.respond(trip.toDto())
        }

        delete("/v1/trips/{tripId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@delete
            }

            val tripId = call.parameters["tripId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            val deleted = TripRepository.deleteTrip(userId, tripId)
            if (!deleted) {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }

            call.respond(HttpStatusCode.NoContent)
        }

        post("/v1/trips/{tripId}/archive") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val tripId = call.parameters["tripId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val archived = TripRepository.archiveTrip(userId, tripId)
            if (!archived) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            call.respond(HttpStatusCode.NoContent)
        }

        post("/v1/trips/{tripId}/transfer-owner") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val tripId = call.parameters["tripId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val request = call.receive<TransferOwnerRequest>()
            val transferred = TripRepository.transferOwner(userId, tripId, request.newOwnerId)
            if (!transferred) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            call.respond(HttpStatusCode.NoContent)
        }

        post("/v1/trips/{tripId}/join") {
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

            val trip = TripRepository.getTripById(normalizedTripId) ?: run {
                call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to mapOf("code" to "trip_not_found", "message" to "Trip not found"))
                )
                return@post
            }

            val role = if (trip.ownerId == userId) "owner" else "member"
            TripRepository.upsertMemberAccepted(normalizedTripId, userId, role)
            call.respond(mapOf("tripId" to normalizedTripId))
        }
    }
}
