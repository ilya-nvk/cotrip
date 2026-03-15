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
import kotlinx.serialization.json.Json
import nvk.cotrip.backend.db.NotificationRepository
import nvk.cotrip.backend.db.NotificationSettingRow
import nvk.cotrip.backend.db.PushTokenRepository

private val notificationJson = Json { ignoreUnknownKeys = true }
private const val READ_BULK_MODE_NON_COMMENT = "non_comment"
private const val READ_BULK_MODE_IDEA_COMMENTS = "idea_comments"

@Serializable
data class NotificationSettingsUpdateRequest(
    val items: List<NotificationSettingDto> = emptyList(),
)

@Serializable
data class NotificationReadBulkRequest(
    val mode: String,
    val ideaId: String? = null,
)

@Serializable
data class NotificationReadBulkResponse(
    val updated: Int,
)

@Serializable
data class PushTokenUpsertRequest(
    val token: String,
    val platform: String = "android",
)

@Serializable
private data class NotificationListResponse(
    val items: List<NotificationDto>,
    val nextCursor: String? = null,
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

            call.respond(NotificationListResponse(items = notifications, nextCursor = null))
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

        post("/v1/notifications/read-bulk") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val request = call.receive<NotificationReadBulkRequest>()
            val mode = request.mode.trim().lowercase()
            val updated = when (mode) {
                READ_BULK_MODE_NON_COMMENT -> NotificationRepository.markReadBulkNonComment(userId)
                READ_BULK_MODE_IDEA_COMMENTS -> {
                    val ideaId = request.ideaId?.trim().orEmpty()
                    if (ideaId.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@post
                    }
                    NotificationRepository.markReadBulkIdeaComments(userId, ideaId)
                }

                else -> {
                    call.respond(HttpStatusCode.BadRequest)
                    return@post
                }
            }

            call.respond(NotificationReadBulkResponse(updated = updated))
        }

        post("/v1/push-tokens") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val request = call.receive<PushTokenUpsertRequest>()
            val token = request.token.trim()
            val platform = request.platform.trim().lowercase()
            if (token.isBlank() || platform.isBlank()) {
                call.respond(HttpStatusCode.BadRequest)
                return@post
            }

            PushTokenRepository.upsert(
                token = token,
                userId = userId,
                platform = platform,
            )
            call.respond(HttpStatusCode.NoContent)
        }

        delete("/v1/push-tokens/{token}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@delete
            }

            val token = call.parameters["token"]?.trim().orEmpty()
            if (token.isBlank()) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }

            PushTokenRepository.removeForUser(token, userId)
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
