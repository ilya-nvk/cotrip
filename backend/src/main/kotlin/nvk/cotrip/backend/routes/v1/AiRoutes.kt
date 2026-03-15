package nvk.cotrip.backend.routes.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import nvk.cotrip.backend.config.AiConfig
import nvk.cotrip.backend.db.AiRepository
import nvk.cotrip.backend.db.AiSuggestionInput
import nvk.cotrip.backend.db.AiSuggestionRow
import nvk.cotrip.backend.db.IdeaRepository
import nvk.cotrip.backend.db.TripRepository
import nvk.cotrip.backend.integrations.YandexAiClient
import nvk.cotrip.backend.integrations.YandexTripSuggestionPrompt

@Serializable
data class AiSuggestionRequest(
    val city: String? = null,
    val description: String? = null,
    val typeOptions: List<String> = emptyList(),
    val timeOfDayOptions: List<String> = emptyList(),
    val budgetOptions: List<String> = emptyList(),
    val generationToken: String? = null,
    val language: String? = null,
)

fun Route.aiRoutes(aiConfig: AiConfig) {
    authenticate("auth-jwt") {
        post("/v1/trips/{tripId}/ai/suggestions") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val tripId = call.parameters["tripId"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val trip = TripRepository.getTripForUser(userId, tripId)
            if (trip == null) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val request = call.receive<AiSuggestionRequest>()
            val provider = aiConfig.provider

            val aiRequest = AiRepository.createRequest(
                tripId = tripId,
                city = request.city,
                description = request.description,
                typeOptions = request.typeOptions.takeIf { it.isNotEmpty() },
                timeOfDayOptions = request.timeOfDayOptions.takeIf { it.isNotEmpty() },
                budgetOptions = request.budgetOptions.takeIf { it.isNotEmpty() },
                provider = provider,
                createdBy = userId,
            )

            val suggestions = runCatching {
                when (provider) {
                    "yandex" -> YandexAiClient.generateSuggestions(
                        config = aiConfig,
                        prompt = YandexTripSuggestionPrompt(
                            city = request.city,
                            description = request.description,
                            typeOptions = request.typeOptions,
                            timeOfDayOptions = request.timeOfDayOptions,
                            budgetOptions = request.budgetOptions,
                            currencyCode = trip.currencyCode,
                            generationToken = request.generationToken,
                            language = request.language,
                            maxSuggestions = aiConfig.maxSuggestions,
                        ),
                    )

                    "mock" -> buildMockSuggestions(
                        request = request,
                        maxSuggestions = aiConfig.maxSuggestions,
                    )

                    else -> throw IllegalArgumentException("Unsupported AI provider: $provider")
                }
            }.getOrElse { error ->
                AiRepository.updateRequestStatus(aiRequest.id, "error", error.message?.take(1_000))
                val yandexError = error as? YandexAiClient.YandexAiException
                val isUnavailable = provider == "yandex" && (
                    ((yandexError != null) && (yandexError.statusCode == 401 || yandexError.statusCode == 403 || yandexError.statusCode == 429 || yandexError.statusCode == 503)) ||
                        (error.message?.contains("YC_AI_API_KEY", ignoreCase = true) == true) ||
                        (error.message?.contains("YC_FOLDER_ID", ignoreCase = true) == true)
                    )
                call.respond(
                    if (isUnavailable) HttpStatusCode.ServiceUnavailable else HttpStatusCode.BadGateway,
                    mapOf(
                        "error" to mapOf(
                            "code" to if (isUnavailable) "ai_provider_unavailable" else "ai_generation_failed",
                            "message" to if (isUnavailable) {
                                "AI provider is not configured"
                            } else {
                                "Unable to generate AI suggestions"
                            },
                        )
                    )
                )
                return@post
            }

            if (suggestions.isEmpty()) {
                AiRepository.updateRequestStatus(aiRequest.id, "error", "No suggestions returned")
                call.respond(
                    HttpStatusCode.BadGateway,
                    mapOf("error" to mapOf("code" to "ai_generation_failed", "message" to "Unable to generate AI suggestions"))
                )
                return@post
            }

            val savedSuggestions = AiRepository.insertSuggestions(aiRequest.id, suggestions)
            AiRepository.updateRequestStatus(aiRequest.id, "done")

            call.respond(mapOf("items" to savedSuggestions.map { it.toDto() }))
        }

        post("/v1/ai/suggestions/{id}/save-to-ideas") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val suggestionId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            val suggestion = AiRepository.getSuggestionWithRequest(suggestionId) ?: run {
                call.respond(HttpStatusCode.NotFound)
                return@post
            }

            if (!TripRepository.isMember(suggestion.tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val idea = IdeaRepository.create(
                tripId = suggestion.tripId,
                authorId = userId,
                title = suggestion.suggestion.title,
                city = suggestion.suggestion.place?.trim()?.ifBlank { null },
                link = null,
                costAmount = suggestion.suggestion.estimatedCost,
                costType = null,
                notes = suggestion.suggestion.description ?: suggestion.requestDescription,
            )

            AiRepository.markSuggestionSaved(suggestionId, idea.id)

            call.respond(
                IdeaDto(
                    id = idea.id,
                    tripId = idea.tripId,
                    authorId = idea.authorId,
                    title = idea.title,
                    city = idea.city,
                    link = idea.link,
                    costAmount = idea.costAmount,
                    costType = idea.costType,
                    notes = idea.notes,
                    status = idea.status,
                    updatedAt = idea.updatedAt.toString(),
                )
            )
        }
    }
}

private fun buildMockSuggestions(request: AiSuggestionRequest, maxSuggestions: Int): List<AiSuggestionInput> {
    val city = request.city?.ifBlank { "" } ?: ""
    val types = if (request.typeOptions.isEmpty()) listOf("Museum", "Cafe", "Walk", "Market") else request.typeOptions
    val budgets = if (request.budgetOptions.isEmpty()) listOf("Budget", "Mid-range", "Premium") else request.budgetOptions
    val times = if (request.timeOfDayOptions.isEmpty()) listOf("Morning", "Afternoon", "Evening") else request.timeOfDayOptions

    return types.take(maxSuggestions).mapIndexed { index, type ->
        val budgetLabel = budgets[index % budgets.size]
        val timeLabel = times[index % times.size]
        val title = if (city.isBlank()) type else "$city $type"
        AiSuggestionInput(
            title = title,
            place = null,
            description = "Suggested $type for $timeLabel in ${if (city.isBlank()) "your trip" else city}.",
            typeLabel = type,
            durationLabel = "2-3 hours",
            budgetLabel = budgetLabel,
            estimatedCost = 15.0 + (index * 10),
        )
    }
}

private fun AiSuggestionRow.toDto(): AiSuggestionDto {
    return AiSuggestionDto(
        id = id,
        title = title,
        place = place,
        description = description,
        typeLabel = typeLabel,
        durationLabel = durationLabel,
        budgetLabel = budgetLabel,
        estimatedCost = estimatedCost,
        isSaved = isSaved,
    )
}
