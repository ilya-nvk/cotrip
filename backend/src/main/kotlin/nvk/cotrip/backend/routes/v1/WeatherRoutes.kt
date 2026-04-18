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
import java.time.LocalDate
import java.time.OffsetDateTime
import nvk.cotrip.backend.config.WeatherConfig
import nvk.cotrip.backend.db.ItineraryDayRepository
import nvk.cotrip.backend.db.TripRepository
import nvk.cotrip.backend.db.WeatherRepository
import nvk.cotrip.backend.integrations.OpenWeatherClient

fun Route.weatherRoutes(weatherConfig: WeatherConfig) {
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

            val city = call.request.queryParameters["city"]?.trim()?.takeIf { it.isNotBlank() } ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val range = resolveRequestedRange(
                tripStart = trip.startDate,
                tripEnd = trip.endDate,
                startParam = call.request.queryParameters["start"],
                endParam = call.request.queryParameters["end"],
            ) ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val start = range.first
            val end = range.second

            call.respond(
                buildWeatherResponse(
                    tripId = tripId,
                    city = city,
                    start = start,
                    end = end,
                    cacheUsed = true,
                    refreshTtlHours = weatherConfig.refreshTtlHours,
                    acceptLanguage = call.request.headers["Accept-Language"],
                    weatherConfig = weatherConfig,
                )
            )
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

            val city = call.request.queryParameters["city"]?.trim()?.takeIf { it.isNotBlank() } ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val range = resolveRequestedRange(
                tripStart = trip.startDate,
                tripEnd = trip.endDate,
                startParam = call.request.queryParameters["start"],
                endParam = call.request.queryParameters["end"],
            ) ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }
            val start = range.first
            val end = range.second

            val coordinates = ItineraryDayRepository.findCityCoordinates(tripId = tripId, city = city)
            if (coordinates == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to mapOf("code" to "city_coordinates_required", "message" to "Select a city from suggestions to save coordinates"))
                )
                return@post
            }

            val availableWindow = resolveProviderWindow(start = start, end = end)
            var cacheUsed = true

            if (availableWindow != null) {
                val availableStart = availableWindow.first
                val availableEnd = availableWindow.second
                val expectedDates = datesBetween(availableStart, availableEnd).toSet()
                val cachedRows = WeatherRepository.list(tripId, city, availableStart, availableEnd)
                val freshAfter = OffsetDateTime.now().minusHours(weatherConfig.refreshTtlHours.toLong())
                val cachedDates = cachedRows.map { it.date }.toSet()
                val isFresh = expectedDates.isNotEmpty() &&
                    expectedDates.all { it in cachedDates } &&
                    cachedRows.all { !it.fetchedAt.isBefore(freshAfter) }

                if (!isFresh) {
                    val apiKey = weatherConfig.openWeatherApiKey
                    if (apiKey.isNullOrBlank()) {
                        call.respond(
                            HttpStatusCode.ServiceUnavailable,
                            mapOf("error" to mapOf("code" to "weather_provider_unavailable", "message" to "OpenWeather is not configured"))
                        )
                        return@post
                    }

                    val forecasts = runCatching {
                        OpenWeatherClient.fetchDailyForecast(
                            apiKey = apiKey,
                            lat = coordinates.cityLat,
                            lon = coordinates.cityLon,
                        )
                    }.getOrElse {
                        call.respond(
                            HttpStatusCode.BadGateway,
                            mapOf("error" to mapOf("code" to "weather_refresh_failed", "message" to "Unable to refresh weather data"))
                        )
                        return@post
                    }

                    forecasts
                        .filter { it.date in expectedDates }
                        .forEach { forecast ->
                            WeatherRepository.upsert(
                                tripId = tripId,
                                city = city,
                                date = forecast.date,
                                tempMin = forecast.tempMin,
                                tempMax = forecast.tempMax,
                                description = forecast.description,
                                iconCode = forecast.iconCode,
                                source = "openweather",
                            )
                        }
                    cacheUsed = false
                }
            }

            call.respond(
                buildWeatherResponse(
                    tripId = tripId,
                    city = city,
                    start = start,
                    end = end,
                    cacheUsed = cacheUsed,
                    refreshTtlHours = weatherConfig.refreshTtlHours,
                    acceptLanguage = call.request.headers["Accept-Language"],
                    weatherConfig = weatherConfig,
                )
            )
        }
    }
}

private fun resolveRequestedRange(
    tripStart: LocalDate,
    tripEnd: LocalDate,
    startParam: String?,
    endParam: String?,
): Pair<LocalDate, LocalDate>? {
    val start = if (startParam.isNullOrBlank()) {
        tripStart
    } else {
        runCatching { LocalDate.parse(startParam) }.getOrNull() ?: return null
    }
    val end = if (endParam.isNullOrBlank()) {
        tripEnd
    } else {
        runCatching { LocalDate.parse(endParam) }.getOrNull() ?: return null
    }
    if (start.isAfter(end)) return null
    return start to end
}

private fun resolveProviderWindow(start: LocalDate, end: LocalDate): Pair<LocalDate, LocalDate>? {
    val today = LocalDate.now()
    val providerEnd = today.plusDays(7)
    val from = if (start.isBefore(today)) today else start
    val to = if (end.isAfter(providerEnd)) providerEnd else end
    return if (from.isAfter(to)) null else from to to
}

private suspend fun buildWeatherResponse(
    tripId: String,
    city: String,
    start: LocalDate,
    end: LocalDate,
    cacheUsed: Boolean,
    refreshTtlHours: Int,
    acceptLanguage: String?,
    weatherConfig: WeatherConfig,
): WeatherForecastResponseDto {
    val rows = WeatherRepository.list(tripId, city, start, end)
    val items = rows.map { it.toDto() }
    val missingDates = datesBetween(start, end)
        .map(LocalDate::toString)
        .filterNot { date -> items.any { it.date == date } }
    val providerWindow = resolveProviderWindow(start, end)
    val nextRefreshAt = rows.maxByOrNull { it.fetchedAt }
        ?.fetchedAt
        ?.plusHours(refreshTtlHours.toLong())
        ?.toString()

    val displayCity = enrichDisplayCity(
        acceptLanguage = acceptLanguage,
        tripId = tripId,
        city = city,
        weatherConfig = weatherConfig,
    )

    return WeatherForecastResponseDto(
        items = items,
        nextCursor = null,
        cacheUsed = cacheUsed,
        availableFrom = providerWindow?.first?.toString(),
        availableTo = providerWindow?.second?.toString(),
        missingDates = missingDates,
        nextRefreshAt = nextRefreshAt,
        displayCity = displayCity,
    )
}

private suspend fun enrichDisplayCity(
    acceptLanguage: String?,
    tripId: String,
    city: String,
    weatherConfig: WeatherConfig,
): String? {
    val header = acceptLanguage?.trim()?.lowercase() ?: return null
    if (!header.startsWith("ru")) return null
    val apiKey = weatherConfig.openWeatherApiKey ?: return null
    val coordinates = ItineraryDayRepository.findCityCoordinates(tripId = tripId, city = city) ?: return null
    return runCatching {
        OpenWeatherClient.reverseLocalCityLabel(
            apiKey = apiKey,
            lat = coordinates.cityLat,
            lon = coordinates.cityLon,
            preferredLang = "ru",
        )
    }.getOrNull()
}

private fun datesBetween(start: LocalDate, end: LocalDate): List<LocalDate> {
    if (start.isAfter(end)) return emptyList()
    val dates = mutableListOf<LocalDate>()
    var current = start
    while (!current.isAfter(end)) {
        dates += current
        current = current.plusDays(1)
    }
    return dates
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
