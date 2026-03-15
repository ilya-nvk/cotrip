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
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import nvk.cotrip.backend.db.SyncRepository
import java.time.OffsetDateTime

@Serializable
data class SyncPushItem(
    val entity: String,
    val id: String,
    val type: String,
    val payload: JsonElement = JsonNull,
)

@Serializable
data class SyncPushRequest(
    val items: List<SyncPushItem> = emptyList(),
)

@Serializable
data class SyncConflict(
    val id: String,
    val reason: String,
)

@Serializable
data class SyncPullResponse(
    val items: List<SyncChangeDto>,
    val nextCursor: String? = null,
)

@Serializable
data class SyncPushResponse(
    val applied: List<String>,
    val conflicts: List<SyncConflict>,
)

fun Route.syncRoutes() {
    authenticate("auth-jwt") {
        get("/v1/sync/changes") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }

            val sinceParam = call.request.queryParameters["since"] ?: run {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val since = runCatching { OffsetDateTime.parse(sinceParam) }.getOrElse {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100)
            val cursor = call.request.queryParameters["cursor"]

            if (limit == null && cursor.isNullOrBlank()) {
                val changes = SyncRepository.listChanges(userId, since).map { change ->
                    SyncChangeDto(
                        entity = change.entity,
                        id = change.id,
                        updatedAt = change.updatedAt.toString(),
                        deletedAt = change.deletedAt?.toString(),
                        payload = change.payload,
                    )
                }
                call.respond(SyncPullResponse(items = changes, nextCursor = null))
            } else {
                val page = SyncRepository.listChangesPage(
                    userId = userId,
                    since = since,
                    limit = limit ?: 100,
                    cursor = cursor,
                )
                val items = page.items.map { change ->
                    SyncChangeDto(
                        entity = change.entity,
                        id = change.id,
                        updatedAt = change.updatedAt.toString(),
                        deletedAt = change.deletedAt?.toString(),
                        payload = change.payload,
                    )
                }
                call.respond(SyncPullResponse(items = items, nextCursor = page.nextCursor))
            }
        }

        post("/v1/sync/changes") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            val request = call.receive<SyncPushRequest>()
            val applied = request.items.map { it.id }
            call.respond(SyncPushResponse(applied = applied, conflicts = emptyList()))
        }
    }
}
