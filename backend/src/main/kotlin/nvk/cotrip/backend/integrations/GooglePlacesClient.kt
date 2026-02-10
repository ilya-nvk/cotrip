package nvk.cotrip.backend.integrations

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class GooglePlaceSuggestion(
    val name: String,
    val placeId: String,
    val fullText: String,
)

object GooglePlacesClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun searchCities(
        query: String,
        apiKey: String,
        limit: Int = 8,
    ): List<GooglePlaceSuggestion> {
        return search(query = query, apiKey = apiKey, limit = limit, citiesOnly = true)
    }

    suspend fun searchPlaces(
        query: String,
        apiKey: String,
        limit: Int = 8,
    ): List<GooglePlaceSuggestion> {
        return search(query = query, apiKey = apiKey, limit = limit, citiesOnly = false)
    }

    private suspend fun search(
        query: String,
        apiKey: String,
        limit: Int,
        citiesOnly: Boolean,
    ): List<GooglePlaceSuggestion> {
        val response = client.get("https://maps.googleapis.com/maps/api/place/autocomplete/json") {
            parameter("input", query)
            if (citiesOnly) {
                parameter("types", "(cities)")
            }
            parameter("key", apiKey)
        }
        if (response.status != HttpStatusCode.OK) {
            throw IllegalStateException("Google Places request failed: ${response.status.value}")
        }

        val payload = response.body<PlacesAutocompleteResponse>()
        if (payload.status !in setOf("OK", "ZERO_RESULTS")) {
            throw IllegalStateException(
                "Google Places returned status=${payload.status}, error=${payload.errorMessage.orEmpty()}"
            )
        }

        return payload.predictions
            .filter { it.placeId.isNotBlank() }
            .map { prediction ->
                GooglePlaceSuggestion(
                    name = prediction.structuredFormatting?.mainText?.takeIf { it.isNotBlank() }
                        ?: prediction.description,
                    placeId = prediction.placeId,
                    fullText = prediction.description,
                )
            }
            .distinctBy { it.placeId }
            .take(limit.coerceIn(1, 20))
    }
}

@Serializable
private data class PlacesAutocompleteResponse(
    val status: String,
    val predictions: List<PlacesPrediction> = emptyList(),
    @SerialName("error_message")
    val errorMessage: String? = null,
)

@Serializable
private data class PlacesPrediction(
    val description: String,
    @SerialName("place_id")
    val placeId: String,
    @SerialName("structured_formatting")
    val structuredFormatting: StructuredFormatting? = null,
)

@Serializable
private data class StructuredFormatting(
    @SerialName("main_text")
    val mainText: String? = null,
)
