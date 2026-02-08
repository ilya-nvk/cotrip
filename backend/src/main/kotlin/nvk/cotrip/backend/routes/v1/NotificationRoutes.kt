package nvk.cotrip.backend.routes.v1

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nvk.cotrip.backend.db.NotificationRepository
import nvk.cotrip.backend.db.NotificationSettingRow

private val notificationJson = Json { ignoreUnknownKeys = true }

@Serializable
data class NotificationSettingsUpdateRequest(
    val items: List<NotificationSettingDto> = emptyList(),
)

fun Route.notificationRoutes() {
    authenticate("auth-jwt") {
        get("/v1/notifications") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val notifications = NotificationRepository.listForUser(userId).map { row ->
                NotificationDto(
                    id = row.id,
                    type = row.type,
                    payload = notificationJson.parseToJsonElement(row.payload),
                    createdAt = row.createdAt.toString(),
                    readAt = row.readAt?.toString(),
                )
            }

            call.respond(mapOf("items" to notifications, "nextCursor" to null))
        }

        patch("/v1/notifications/{id}/read") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@patch
            }

            val notificationId = call.parameters["id"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@patch
            }

            val updated = NotificationRepository.markRead(userId, notificationId)
            if (!updated) {
                call.respond(HttpStatusCode.NotFound)
                return@patch
            }

            call.respond(HttpStatusCode.NoContent)
        }

        get("/v1/users/me/notification-settings") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val settings = NotificationRepository.listSettings(userId).map { setting ->
                NotificationSettingDto(
                    key = setting.key,
                    enabled = setting.enabled,
                )
            }

            call.respond(mapOf("items" to settings))
        }

        patch("/v1/users/me/notification-settings") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@patch
            }

            val request = call.receive<NotificationSettingsUpdateRequest>()
            val rows = request.items.map { item ->
                NotificationSettingRow(
                    userId = userId,
                    key = item.key,
                    enabled = item.enabled,
                )
            }

            NotificationRepository.upsertSettings(userId, rows)
            val updatedSettings = NotificationRepository.listSettings(userId).map { setting ->
                NotificationSettingDto(
                    key = setting.key,
                    enabled = setting.enabled,
                )
            }

            call.respond(mapOf("items" to updatedSettings))
        }
    }
}
