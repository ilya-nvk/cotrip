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
import nvk.cotrip.backend.db.AiRepository
import nvk.cotrip.backend.db.AiSuggestionInput
import nvk.cotrip.backend.db.AiSuggestionRow
import nvk.cotrip.backend.db.IdeaRepository
import nvk.cotrip.backend.db.TripRepository

@Serializable
data class AiSuggestionRequest(
    val city: String? = null,
    val description: String? = null,
    val typeOptions: List<String> = emptyList(),
    val timeOfDayOptions: List<String> = emptyList(),
    val budgetOptions: List<String> = emptyList(),
)

fun Route.aiRoutes() {
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

            if (!TripRepository.isMember(tripId, userId)) {
                call.respond(HttpStatusCode.Forbidden)
                return@post
            }

            val request = call.receive<AiSuggestionRequest>()
            val provider = System.getenv("ALICE_AI_PROVIDER") ?: "mock"

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

            val suggestions = buildSuggestions(request).map { suggestion ->
                AiSuggestionInput(
                    title = suggestion.title,
                    description = suggestion.description,
                    typeLabel = suggestion.typeLabel,
                    durationLabel = suggestion.durationLabel,
                    budgetLabel = suggestion.budgetLabel,
                    estimatedCost = suggestion.estimatedCost,
                )
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
                city = suggestion.city,
                link = null,
                costAmount = suggestion.suggestion.estimatedCost,
                costType = null,
                website = null,
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
                    website = idea.website,
                    notes = idea.notes,
                    status = idea.status,
                    updatedAt = idea.updatedAt.toString(),
                )
            )
        }
    }
}

private fun buildSuggestions(request: AiSuggestionRequest): List<AiSuggestionDto> {
    val city = request.city?.ifBlank { "" } ?: ""
    val types = if (request.typeOptions.isEmpty()) listOf("Museum", "Cafe", "Walk", "Market") else request.typeOptions
    val budgets = if (request.budgetOptions.isEmpty()) listOf("Budget", "Mid-range", "Premium") else request.budgetOptions
    val times = if (request.timeOfDayOptions.isEmpty()) listOf("Morning", "Afternoon", "Evening") else request.timeOfDayOptions

    return types.take(5).mapIndexed { index, type ->
        val budgetLabel = budgets[index % budgets.size]
        val timeLabel = times[index % times.size]
        val title = if (city.isBlank()) type else "$city $type"
        AiSuggestionDto(
            id = "",
            title = title,
            description = "Suggested $type for $timeLabel in ${if (city.isBlank()) "your trip" else city}.",
            typeLabel = type,
            durationLabel = "2-3 hours",
            budgetLabel = budgetLabel,
            estimatedCost = 15.0 + (index * 10),
            isSaved = false,
        )
    }
}

private fun AiSuggestionRow.toDto(): AiSuggestionDto {
    return AiSuggestionDto(
        id = id,
        title = title,
        description = description,
        typeLabel = typeLabel,
        durationLabel = durationLabel,
        budgetLabel = budgetLabel,
        estimatedCost = estimatedCost,
        isSaved = isSaved,
    )
}
