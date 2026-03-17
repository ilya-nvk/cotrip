package nvk.cotrip.backend.testing

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nvk.cotrip.backend.module
import kotlin.random.Random
import kotlin.test.assertEquals

object TestApplicationSupport {
    val json: Json = Json { ignoreUnknownKeys = true }

    data class DevSession(
        val accessToken: String,
        val refreshToken: String,
        val userId: String,
    )

    suspend fun ApplicationTestBuilder.createDevSession(
        googleId: String = "integration-${System.currentTimeMillis()}-${Random.nextInt(1_000_000)}",
        name: String = "Integration Test User",
    ): DevSession {
        val tokenResponse = client.post("/v1/auth/dev") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "googleId": "$googleId",
                  "name": "$name"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, tokenResponse.status)
        val tokenBody = json.parseToJsonElement(tokenResponse.body<String>()).jsonObject
        return DevSession(
            accessToken = tokenBody["accessToken"]!!.jsonPrimitive.content,
            refreshToken = tokenBody["refreshToken"]!!.jsonPrimitive.content,
            userId = tokenBody["user"]!!.jsonObject["id"]!!.jsonPrimitive.content,
        )
    }

    fun withApp(
        block: suspend ApplicationTestBuilder.(DevSession) -> Unit,
    ) {
        testApplication {
            environment {
                config = MapApplicationConfig(
                    "ktor.db.url" to PostgresContainerSupport.jdbcUrl(),
                    "ktor.db.user" to PostgresContainerSupport.username(),
                    "ktor.db.password" to PostgresContainerSupport.password(),
                    "ktor.devAuthEnabled" to "true",
                    "ktor.jwt.secret" to "test-secret",
                )
            }
            application {
                module()
            }

            block(createDevSession())
        }
    }
}
