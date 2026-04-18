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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nvk.cotrip.backend.ai.AiRequestPolicyEvaluator
import nvk.cotrip.backend.ai.AiRequestRelevanceClassifier
import nvk.cotrip.backend.ai.AiRequestRelevanceInput
import nvk.cotrip.backend.ai.AiSuggestionPostFilter
import nvk.cotrip.backend.ai.AiSuggestionPostFilterRequest
import nvk.cotrip.backend.config.AiConfig
import nvk.cotrip.backend.db.AiRepository
import nvk.cotrip.backend.db.AiSuggestionInput
import nvk.cotrip.backend.db.AiSuggestionRow
import nvk.cotrip.backend.db.IdeaRepository
import nvk.cotrip.backend.db.ItineraryDayRepository
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

            val request = call.receive<AiSuggestionRequest>().normalized()
            val provider = aiConfig.provider
            val itineraryCities = ItineraryDayRepository.listByTrip(tripId)
                .mapNotNull { it.city?.trim()?.takeIf { city -> city.isNotBlank() } }
                .distinct()

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

            val requestPolicyDecision = AiRequestPolicyEvaluator.evaluate(
                city = request.city,
                description = request.description,
                typeOptions = request.typeOptions,
                timeOfDayOptions = request.timeOfDayOptions,
                budgetOptions = request.budgetOptions,
            )
            if (!requestPolicyDecision.isAllowed) {
                val category = checkNotNull(requestPolicyDecision.category).wireValue
                AiRepository.updateRequestStatus(aiRequest.id, "error", "Request blocked by AI policy: $category")
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    errorResponse(
                        code = "ai_policy_violation",
                        message = "Request violates AI suggestion policy",
                        details = buildJsonObject {
                            put("stage", "request")
                            put("category", category)
                        }
                    )
                )
                return@post
            }

            val requestRelevance = runCatching {
                AiRequestRelevanceClassifier.classify(
                    provider = provider,
                    config = aiConfig,
                    input = AiRequestRelevanceInput(
                        city = request.city,
                        description = request.description,
                        typeOptions = request.typeOptions,
                        timeOfDayOptions = request.timeOfDayOptions,
                        budgetOptions = request.budgetOptions,
                        generationToken = request.generationToken,
                    ),
                )
            }.getOrNull()
            if (AiRequestRelevanceClassifier.shouldBlockAsOffTopic(requestRelevance)) {
                AiRepository.updateRequestStatus(aiRequest.id, "error", "Request blocked by relevance classifier: off_topic")
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    errorResponse(
                        code = "ai_policy_violation",
                        message = "Request violates AI suggestion policy",
                        details = buildJsonObject {
                            put("stage", "request")
                            put("category", "off_topic")
                        }
                    )
                )
                return@post
            }

            val rawSuggestions = runCatching {
                when (provider) {
                    "yandex" -> YandexAiClient.generateSuggestions(
                        config = aiConfig,
                        prompt = YandexTripSuggestionPrompt(
                            city = request.city,
                            itineraryCities = itineraryCities,
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
                        itineraryCities = itineraryCities,
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
                    errorResponse(
                        code = if (isUnavailable) "ai_provider_unavailable" else "ai_generation_failed",
                        message = if (isUnavailable) {
                            "AI provider is not configured"
                        } else {
                            "Unable to generate AI suggestions"
                        }
                    )
                )
                return@post
            }

            if (rawSuggestions.isEmpty()) {
                AiRepository.updateRequestStatus(aiRequest.id, "error", "No suggestions returned")
                call.respond(
                    HttpStatusCode.BadGateway,
                    errorResponse(
                        code = "ai_generation_failed",
                        message = "Unable to generate AI suggestions",
                    )
                )
                return@post
            }

            val filteredSuggestions = AiSuggestionPostFilter.filter(
                request = AiSuggestionPostFilterRequest(
                    city = request.city,
                    itineraryCities = itineraryCities,
                    typeOptions = request.typeOptions,
                    timeOfDayOptions = request.timeOfDayOptions,
                    budgetOptions = request.budgetOptions,
                ),
                suggestions = rawSuggestions,
            )
            if (filteredSuggestions.kept.isEmpty()) {
                AiRepository.updateRequestStatus(aiRequest.id, "error", "No relevant suggestions remained after filtering")
                call.respond(
                    HttpStatusCode.UnprocessableEntity,
                    errorResponse(
                        code = "ai_no_relevant_results",
                        message = "No safe and relevant AI suggestions are available",
                        details = buildJsonObject {
                            put("stage", "response")
                            put("generatedCount", filteredSuggestions.generatedCount)
                            put("keptCount", filteredSuggestions.keptCount)
                            put(
                                "topRejectReasons",
                                JsonArray(filteredSuggestions.topRejectReasons.map(::JsonPrimitive))
                            )
                        }
                    )
                )
                return@post
            }

            val savedSuggestions = AiRepository.insertSuggestions(aiRequest.id, filteredSuggestions.kept)
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

private fun buildMockSuggestions(
    request: AiSuggestionRequest,
    itineraryCities: List<String>,
    maxSuggestions: Int,
): List<AiSuggestionInput> {
    when (request.generationToken?.trim()) {
        "test-generation-error" -> error("Mock AI generation failed")
        "test-mixed-output" -> {
            val requestedCity = request.city?.trim().orEmpty().ifBlank { "Rome" }
            val mismatchCity = itineraryCities.firstOrNull { !it.equals(requestedCity, ignoreCase = true) } ?: "Florence"
            return listOf(
                AiSuggestionInput(
                    title = "Vatican Museums quiet start",
                    place = "Viale Vaticano 100, $requestedCity",
                    description = "Morning museum route with calmer galleries and a short espresso break.",
                    typeLabel = request.typeOptions.firstOrNull() ?: "Museums",
                    durationLabel = "2-3 hours",
                    budgetLabel = request.budgetOptions.firstOrNull() ?: "€€",
                    estimatedCost = 28.0,
                ),
                AiSuggestionInput(
                    title = "Buy cocaine from a hidden local contact",
                    place = null,
                    description = "An illegal late-night hookup for drugs.",
                    typeLabel = "Night",
                    durationLabel = "1 hour",
                    budgetLabel = "€€€",
                    estimatedCost = 120.0,
                ),
                AiSuggestionInput(
                    title = "$mismatchCity sunset walk",
                    place = "Central square, $mismatchCity",
                    description = "Evening walk in a different city from the selected one.",
                    typeLabel = "Must-see",
                    durationLabel = "2 hours",
                    budgetLabel = "Free",
                    estimatedCost = 0.0,
                ),
            ).take(maxSuggestions)
        }

        "test-all-filtered-output" -> {
            val requestedCity = request.city?.trim().orEmpty().ifBlank { "Rome" }
            val mismatchCity = itineraryCities.firstOrNull { !it.equals(requestedCity, ignoreCase = true) } ?: "Florence"
            return listOf(
                AiSuggestionInput(
                    title = "As an AI, I suggest checking weather apps first",
                    place = null,
                    description = "Use Google Maps and compare hotel prices before deciding.",
                    typeLabel = "Random",
                    durationLabel = "Any",
                    budgetLabel = "€",
                    estimatedCost = 0.0,
                ),
                AiSuggestionInput(
                    title = "Buy illegal fireworks downtown",
                    place = null,
                    description = "Dangerous underground purchase.",
                    typeLabel = "Night",
                    durationLabel = "1 hour",
                    budgetLabel = "€€€",
                    estimatedCost = 90.0,
                ),
                AiSuggestionInput(
                    title = "$mismatchCity bar crawl",
                    place = "Old town, $mismatchCity",
                    description = "Late-night bar route in another city.",
                    typeLabel = "Night",
                    durationLabel = "3 hours",
                    budgetLabel = "€€",
                    estimatedCost = 45.0,
                ),
            ).take(maxSuggestions)
        }
    }

    val city = request.city?.ifBlank { "" } ?: ""
    val types = if (request.typeOptions.isEmpty()) listOf("Museum", "Cafe", "Walk", "Market") else request.typeOptions
    val budgets = if (request.budgetOptions.isEmpty()) listOf("Free", "€", "€€", "€€€") else request.budgetOptions
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

private fun errorResponse(
    code: String,
    message: String,
    details: kotlinx.serialization.json.JsonObject? = null,
): ErrorResponseDto {
    return ErrorResponseDto(
        error = ErrorDto(
            code = code,
            message = message,
            details = details,
        )
    )
}

private fun AiSuggestionRequest.normalized(): AiSuggestionRequest {
    return copy(
        city = city?.trim()?.ifBlank { null },
        description = description?.trim()?.ifBlank { null },
        typeOptions = typeOptions.mapNotNull { it.trim().ifBlank { null } }.distinct(),
        timeOfDayOptions = timeOfDayOptions.mapNotNull { it.trim().ifBlank { null } }.distinct(),
        budgetOptions = budgetOptions.mapNotNull { it.trim().ifBlank { null } }.distinct(),
        generationToken = generationToken?.trim()?.ifBlank { null },
        language = language?.trim()?.ifBlank { null },
    )
}
