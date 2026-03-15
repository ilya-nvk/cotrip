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
import nvk.cotrip.backend.config.WeatherConfig
import nvk.cotrip.backend.db.ActivityRepository
import nvk.cotrip.backend.db.ActivityRow
import nvk.cotrip.backend.db.DayRepository
import nvk.cotrip.backend.db.ItineraryDayRepository
import nvk.cotrip.backend.db.LocalPlacesSearchRepository
import nvk.cotrip.backend.db.TripRepository
import nvk.cotrip.backend.integrations.OpenWeatherClient

@Serializable
data class UpdateDayRequest(
    val city: String? = null,
    val cityProviderId: String? = null,
    val cityLat: Double? = null,
    val cityLon: Double? = null,
)

@Serializable
data class CreateActivityRequest(
    val title: String,
    val timeText: String? = null,
    val locationName: String? = null,
    val link: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val notes: String? = null,
    val orderIndex: Int? = null,
)

@Serializable
data class UpdateActivityRequest(
    val title: String? = null,
    val timeText: String? = null,
    val locationName: String? = null,
    val link: String? = null,
    val costAmount: Double? = null,
    val costType: String? = null,
    val notes: String? = null,
)

@Serializable
data class MoveActivityRequest(
    val dayId: String,
    val orderIndex: Int? = null,
)

@Serializable
data class ReorderRequest(
    val orderedIds: List<String>,
)

@Serializable
data class TrimOutOfRangeRequest(
    val action: String,
    val dayIds: List<String>,
)

fun Route.itineraryRoutes(weatherConfig: WeatherConfig) {
    authenticate("auth-jwt") {
        get("/v1/trips/{tripId}/cities/search") {
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

            val query = call.request.queryParameters["query"]?.trim().orEmpty()
            if (query.isBlank()) {
                call.respond(mapOf("items" to emptyList<CitySuggestionDto>()))
                return@get
            }

            val apiKey = weatherConfig.openWeatherApiKey
            if (apiKey.isNullOrBlank()) {
                call.respond(
                    HttpStatusCode.ServiceUnavailable,
                    mapOf("error" to mapOf("code" to "weather_provider_unavailable", "message" to "Weather provider is not configured"))
                )
                return@get
            }

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 20) ?: 8
            val suggestions = runCatching {
                OpenWeatherClient.searchCities(
                    apiKey = apiKey,
                    query = query,
                    limit = limit,
                )
            }.getOrElse {
                call.respond(
                    HttpStatusCode.BadGateway,
                    mapOf("error" to mapOf("code" to "city_search_error", "message" to "Unable to fetch city suggestions"))
                )
                return@get
            }

            call.respond(
                mapOf(
                    "items" to suggestions.map {
                        CitySuggestionDto(
                            name = it.name,
                            providerId = it.providerId,
                            lat = it.lat,
                            lon = it.lon,
                            fullText = it.fullText,
                        )
                    }
                )
            )
        }

        get("/v1/trips/{tripId}/places/search") {
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

            val query = call.request.queryParameters["query"]?.trim().orEmpty()
            if (query.isBlank()) {
                call.respond(mapOf("items" to emptyList<PlaceSuggestionDto>()))
                return@get
            }

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 20) ?: 8
            val suggestions = runCatching {
                LocalPlacesSearchRepository.searchPlaces(tripId = tripId, query = query, limit = limit)
            }.getOrElse {
                call.respond(
                    HttpStatusCode.BadGateway,
                    mapOf("error" to mapOf("code" to "place_search_error", "message" to "Unable to fetch place suggestions"))
                )
                return@get
            }

            call.respond(
                mapOf(
                    "items" to suggestions.map {
                        PlaceSuggestionDto(
                            name = it.name,
                            placeId = it.placeId,
                            fullText = it.fullText,
                        )
                    }
                )
            )
        }

        get("/v1/trips/{tripId}/itinerary") {
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

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100)
            val cursor = call.request.queryParameters["cursor"]
            if (limit == null && cursor.isNullOrBlank()) {
                val days = ItineraryDayRepository.listByTrip(tripId)
                val activities = ActivityRepository.listByDayIds(days.map { it.id })
                val activitiesByDay = activities.groupBy { it.dayId }
                val result = days.map { day ->
                    ItineraryDayDto(
                        id = day.id,
                        tripId = day.tripId,
                        date = day.date.toString(),
                        dayNumber = day.dayNumber,
                        city = day.city,
                        cityProviderId = day.cityProviderId,
                        cityLat = day.cityLat,
                        cityLon = day.cityLon,
                        isOutOfRange = day.isOutOfRange,
                        activities = activitiesByDay[day.id].orEmpty().map { it.toDto() },
                    )
                }
                call.respond(mapOf("items" to result, "nextCursor" to null))
            } else {
                val page = ItineraryDayRepository.listByTripPage(
                    tripId = tripId,
                    limit = limit ?: 100,
                    cursor = cursor,
                )
                val activities = ActivityRepository.listByDayIds(page.items.map { it.id })
                val activitiesByDay = activities.groupBy { it.dayId }
                val result = page.items.map { day ->
                    ItineraryDayDto(
                        id = day.id,
                        tripId = day.tripId,
                        date = day.date.toString(),
                        dayNumber = day.dayNumber,
                        city = day.city,
                        cityProviderId = day.cityProviderId,
                        cityLat = day.cityLat,
                        cityLon = day.cityLon,
                        isOutOfRange = day.isOutOfRange,
                        activities = activitiesByDay[day.id].orEmpty().map { it.toDto() },
                    )
                }
                call.respond(
                    mapOf(
                        "items" to result,
                        "nextCursor" to page.nextCursor,
                    )
                )
            }
        }

        patch("/v1/itinerary/days/{dayId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@patch
            }

            val dayId = call.parameters["dayId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@patch
            }

            val tripId = DayRepository.findTripIdByDayId(dayId)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            if (!TripRepository.isMember(tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@patch
            }

            val request = call.receive<UpdateDayRequest>()
            val normalizedCity = request.city?.trim()?.ifBlank { null }
            val normalizedProviderId = request.cityProviderId?.trim()?.ifBlank { null }
            val updated = if (normalizedCity == null) {
                ItineraryDayRepository.updateCity(
                    dayId = dayId,
                    city = null,
                    cityProviderId = null,
                    cityLat = null,
                    cityLon = null,
                )
            } else {
                if (request.cityLat == null || request.cityLon == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to mapOf("code" to "city_coordinates_required", "message" to "Coordinates are required for selected city"))
                    )
                    return@patch
                }
                ItineraryDayRepository.updateCity(
                    dayId = dayId,
                    city = normalizedCity,
                    cityProviderId = normalizedProviderId,
                    cityLat = request.cityLat,
                    cityLon = request.cityLon,
                )
            }
            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            call.respond(HttpStatusCode.NoContent)
        }

        post("/v1/itinerary/days/{dayId}/activities") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val dayId = call.parameters["dayId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val tripId = DayRepository.findTripIdByDayId(dayId)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            if (!TripRepository.isMember(tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val request = call.receive<CreateActivityRequest>()
            val orderIndex = request.orderIndex ?: ActivityRepository.nextOrderIndex(dayId)
            val activity = ActivityRepository.create(
                dayId = dayId,
                title = request.title,
                timeText = request.timeText,
                locationName = request.locationName,
                link = request.link,
                costAmount = request.costAmount,
                costType = request.costType,
                notes = request.notes,
                orderIndex = orderIndex,
            )

            call.respond(activity.toDto())
        }

        patch("/v1/itinerary/activities/{activityId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@patch
            }

            val activityId = call.parameters["activityId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@patch
            }

            val existing = ActivityRepository.get(activityId)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            val tripId = DayRepository.findTripIdByDayId(existing.dayId)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            if (!TripRepository.isMember(tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@patch
            }

            val request = call.receive<UpdateActivityRequest>()
            val updated = ActivityRepository.update(
                activityId = activityId,
                title = request.title,
                timeText = request.timeText,
                locationName = request.locationName,
                link = request.link,
                costAmount = request.costAmount,
                costType = request.costType,
                notes = request.notes,
            )

            if (updated == null) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            call.respond(updated.toDto())
        }

        delete("/v1/itinerary/activities/{activityId}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@delete
            }

            val activityId = call.parameters["activityId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            val existing = ActivityRepository.get(activityId)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            val tripId = DayRepository.findTripIdByDayId(existing.dayId)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            if (!TripRepository.isMember(tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@delete
            }

            val deleted = ActivityRepository.softDelete(activityId)
            if (!deleted) {
                call.respond(HttpStatusCode.NotFound)
                return@delete
            }

            call.respond(HttpStatusCode.NoContent)
        }

        post("/v1/itinerary/activities/{activityId}/move") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val activityId = call.parameters["activityId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val existing = ActivityRepository.get(activityId)
            if (existing == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val activityTripId = DayRepository.findTripIdByDayId(existing.dayId)
            if (activityTripId == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            if (!TripRepository.isMember(activityTripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val request = call.receive<MoveActivityRequest>()
            val targetTripId = DayRepository.findTripIdByDayId(request.dayId)
            if (targetTripId == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            if (targetTripId != activityTripId) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to mapOf("code" to "invalid_day", "message" to "Day does not belong to activity trip"))
                )
                return@post
            }

            val orderIndex = request.orderIndex ?: ActivityRepository.nextOrderIndex(request.dayId)
            val moved = ActivityRepository.move(activityId, request.dayId, orderIndex)
            if (moved == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            call.respond(moved.toDto())
        }

        post("/v1/itinerary/days/{dayId}/activities/reorder") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val dayId = call.parameters["dayId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val tripId = DayRepository.findTripIdByDayId(dayId)
            if (tripId == null) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            if (!TripRepository.isMember(tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val request = call.receive<ReorderRequest>()
            ActivityRepository.reorder(dayId, request.orderedIds)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/v1/trips/{tripId}/itinerary/trim-out-of-range") {
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

            val request = call.receive<TrimOutOfRangeRequest>()
            when (request.action) {
                "keep" -> ItineraryDayRepository.markOutOfRange(request.dayIds, true)
                "remove" -> ItineraryDayRepository.deleteDays(request.dayIds)
                "extend_end" -> {
                    val updated = TripRepository.extendTripEndByOutOfRangeDays(
                        ownerId = userId,
                        tripId = tripId,
                        dayIds = request.dayIds,
                    )
                    if (updated == null) {
                        call.respond(HttpStatusCode.Forbidden)
                        return@post
                    }
                }
                else -> {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf(
                            "error" to mapOf(
                                "code" to "invalid_action",
                                "message" to "Action must be keep, remove, or extend_end",
                            )
                        )
                    )
                    return@post
                }
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ActivityRow.toDto(): ActivityDto = ActivityDto(
    id = id,
    dayId = dayId,
    sourceIdeaId = sourceIdeaId,
    title = title,
    timeText = timeText,
    locationName = locationName,
    link = link,
    costAmount = costAmount,
    costType = costType,
    notes = notes,
    orderIndex = orderIndex,
)
