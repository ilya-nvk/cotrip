package nvk.cotrip.backend.integrations

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenWeatherClientTest {

    @AfterTest
    fun tearDown() {
        OpenWeatherClient.httpClientForTest = null
    }

    @Test
    fun given_emptyResponse_when_searchCities_then_returnsEmptyList() = runBlocking {
        // GIVEN
        val mockEngine = MockEngine { respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        OpenWeatherClient.httpClientForTest = io.ktor.client.HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // WHEN
        val result = OpenWeatherClient.searchCities(apiKey = "key", query = "Paris", limit = 5)

        // THEN
        assertTrue(result.isEmpty())
    }

    @Test
    fun given_validResponse_when_searchCities_then_mapsToCandidates() = runBlocking {
        // GIVEN
        val json = """[{"name":"Paris","lat":48.85,"lon":2.35,"country":"FR","state":null}]"""
        val mockEngine = MockEngine { respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        OpenWeatherClient.httpClientForTest = io.ktor.client.HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // WHEN
        val result = OpenWeatherClient.searchCities(apiKey = "key", query = "Paris", limit = 5)

        // THEN
        assertEquals(1, result.size)
        assertEquals("Paris", result.first().name)
        assertEquals(48.85, result.first().lat)
        assertEquals("Paris, FR", result.first().fullText)
        assertEquals("owm:48.85:2.35", result.first().providerId)
    }

    @Test
    fun given_localNames_when_searchCitiesWithPreferredLang_then_usesLocalizedName() = runBlocking {
        val json =
            """[{"name":"Moscow","local_names":{"en":"Moscow","ru":"Москва"},"lat":55.75,"lon":37.62,"country":"RU","state":null}]"""
        val mockEngine = MockEngine { respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        OpenWeatherClient.httpClientForTest = io.ktor.client.HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val ru = OpenWeatherClient.searchCities(apiKey = "key", query = "Moscow", limit = 5, preferredLang = "ru")
        val en = OpenWeatherClient.searchCities(apiKey = "key", query = "Moscow", limit = 5, preferredLang = "en")

        assertEquals("Москва", ru.first().name)
        assertEquals("Moscow", en.first().name)
    }

    @Test
    fun given_limitOutOfRange_when_searchCities_then_coercesToValidRange() = runBlocking {
        // GIVEN — client coerces limit to 1..20; we just verify no throw
        val mockEngine = MockEngine { respond("[]", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        OpenWeatherClient.httpClientForTest = io.ktor.client.HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // WHEN
        val result0 = OpenWeatherClient.searchCities(apiKey = "key", query = "x", limit = 0)
        val result25 = OpenWeatherClient.searchCities(apiKey = "key", query = "x", limit = 25)

        // THEN
        assertTrue(result0.isEmpty())
        assertTrue(result25.isEmpty())
    }

    @Test
    fun given_emptyDaily_when_fetchDailyForecast_then_returnsEmptyList() = runBlocking {
        // GIVEN
        val json = """{"timezone_offset":0,"daily":[]}"""
        val mockEngine = MockEngine { respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        OpenWeatherClient.httpClientForTest = io.ktor.client.HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // WHEN
        val result = OpenWeatherClient.fetchDailyForecast(apiKey = "key", lat = 48.85, lon = 2.35)

        // THEN
        assertTrue(result.isEmpty())
    }

    @Test
    fun given_validDailyResponse_when_fetchDailyForecast_then_mapsToForecasts() = runBlocking {
        // GIVEN — dt is epoch sec; timezone_offset 7200 (UTC+2), so dt=0 -> 1970-01-01 02:00 UTC -> date 1970-01-01
        val json = """{"timezone_offset":7200,"daily":[{"dt":0,"temp":{"min":1.0,"max":10.0},"weather":[{"description":"clear","icon":"01d"}]}]}"""
        val mockEngine = MockEngine { respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        OpenWeatherClient.httpClientForTest = io.ktor.client.HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        // WHEN
        val result = OpenWeatherClient.fetchDailyForecast(apiKey = "key", lat = 48.85, lon = 2.35)

        // THEN
        assertEquals(1, result.size)
        assertEquals(1.0, result[0].tempMin)
        assertEquals(10.0, result[0].tempMax)
        assertEquals("clear", result[0].description)
        assertEquals("01d", result[0].iconCode)
    }
}
