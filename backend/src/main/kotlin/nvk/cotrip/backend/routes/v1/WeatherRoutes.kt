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
import nvk.cotrip.backend.db.TripRepository
import nvk.cotrip.backend.db.WeatherRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private data class ForecastSeed(
    val date: LocalDate,
    val tempMin: Double?,
    val tempMax: Double?,
    val description: String?,
    val iconCode: String?,
)

fun Route.weatherRoutes() {
    authenticate("auth-jwt") {
        get("/v1/trips/{tripId}/weather") {
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

            val trip = TripRepository.getTripById(tripId) ?: run {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }

            val city = call.request.queryParameters["city"]?.takeIf { it.isNotBlank() } ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val startParam = call.request.queryParameters["start"]
            val endParam = call.request.queryParameters["end"]
            val start = startParam?.let { LocalDate.parse(it) } ?: trip.startDate
            val end = endParam?.let { LocalDate.parse(it) } ?: trip.endDate

            val forecasts = WeatherRepository.list(tripId, city, start, end).map { forecast ->
                forecast.toDto()
            }

            call.respond(mapOf("items" to forecasts, "nextCursor" to null))
        }

        post("/v1/trips/{tripId}/weather/refresh") {
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

            val trip = TripRepository.getTripById(tripId) ?: run {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            val city = call.request.queryParameters["city"]?.takeIf { it.isNotBlank() } ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val startParam = call.request.queryParameters["start"]
            val endParam = call.request.queryParameters["end"]
            val start = startParam?.let { LocalDate.parse(it) } ?: trip.startDate
            val end = endParam?.let { LocalDate.parse(it) } ?: trip.endDate

            val source = System.getenv("WEATHER_SOURCE") ?: "mock"
            val seeds = generateForecasts(city, start, end)
            seeds.forEach { seed ->
                WeatherRepository.upsert(
                    tripId = tripId,
                    city = city,
                    date = seed.date,
                    tempMin = seed.tempMin,
                    tempMax = seed.tempMax,
                    description = seed.description,
                    iconCode = seed.iconCode,
                    source = source,
                )
            }

            val forecasts = WeatherRepository.list(tripId, city, start, end).map { it.toDto() }
            call.respond(mapOf("items" to forecasts, "nextCursor" to null))
        }
    }
}

private fun generateForecasts(city: String, start: LocalDate, end: LocalDate): List<ForecastSeed> {
    val days = ChronoUnit.DAYS.between(start, end)
    if (days < 0) return emptyList()
    val base = 12 + (abs(city.lowercase().hashCode()) % 8)
    val descriptions = listOf("Sunny", "Partly cloudy", "Cloudy", "Light rain", "Clear sky")
    val icons = listOf("sunny", "partly_cloudy", "cloudy", "rain", "clear")

    return (0..days).map { offset ->
        val tempMin = (base + (offset % 5)).toDouble()
        val tempMax = tempMin + 6.0
        val idx = (offset % descriptions.size).toInt()
        ForecastSeed(
            date = start.plusDays(offset),
            tempMin = tempMin,
            tempMax = tempMax,
            description = descriptions[idx],
            iconCode = icons[idx],
        )
    }
}

private fun nvk.cotrip.backend.db.WeatherForecastRow.toDto(): WeatherForecastDto {
    return WeatherForecastDto(
        id = id,
        tripId = tripId,
        city = city,
        date = date.toString(),
        tempMin = tempMin,
        tempMax = tempMax,
        description = description,
        iconCode = iconCode,
        source = source,
        fetchedAt = fetchedAt.toString(),
    )
}
