package nvk.cotrip.backend.routes.v1

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.streamProvider
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.http.content.staticFiles
import io.ktor.server.request.header
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import nvk.cotrip.backend.config.MediaConfig
import java.io.File
import java.util.UUID

@Serializable
data class UploadImageResponse(
    val url: String,
)

fun Route.mediaRoutes(config: MediaConfig) {
    val uploadDir = File(config.uploadDir).absoluteFile
    if (!uploadDir.exists()) {
        uploadDir.mkdirs()
    }
    staticFiles("/uploads", uploadDir)

    authenticate("auth-jwt") {
        post("/v1/uploads/images") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.getClaim("userId", String::class) ?: run {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }

            if (!uploadDir.exists() && !uploadDir.mkdirs()) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to mapOf("code" to "internal_error", "message" to "Unable to prepare upload directory"))
                )
                return@post
            }

            val multipart = call.receiveMultipart()
            var uploadedUrl: String? = null
            var uploaded = false
            var errorStatus: HttpStatusCode? = null
            var errorBody: Map<String, Map<String, String>>? = null

            while (true) {
                val part = multipart.readPart() ?: break
                if (errorStatus != null) {
                    part.dispose()
                    continue
                }
                if (!uploaded && part is io.ktor.http.content.PartData.FileItem && part.name == "file") {
                    val contentType = part.contentType
                    if (contentType?.contentType != ContentType.Image.Any.contentType) {
                        errorStatus = HttpStatusCode.BadRequest
                        errorBody = mapOf("error" to mapOf("code" to "invalid_image", "message" to "Only image files are supported"))
                        part.dispose()
                        continue
                    }

                    val extension = resolveFileExtension(part.originalFileName, contentType)
                    val fileName = "${UUID.randomUUID()}-$userId$extension"
                    val destination = File(uploadDir, fileName)
                    var sizeBytes = 0L
                    try {
                        part.streamProvider().use { input ->
                            destination.outputStream().buffered().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                sizeBytes += read.toLong()
                                if (sizeBytes > config.maxUploadBytes) {
                                    throw IllegalArgumentException("Image is too large")
                                }
                                output.write(buffer, 0, read)
                            }
                            }
                        }
                        val scheme = call.request.header("X-Forwarded-Proto")
                            ?.substringBefore(',')
                            ?.trim()
                            ?.ifBlank { null }
                            ?: "https"
                        val host = call.request.header(HttpHeaders.Host)
                            ?.substringBefore(',')
                            ?.trim()
                            ?.ifBlank { null }
                            ?: "localhost"
                        uploadedUrl = "$scheme://$host/uploads/$fileName"
                        uploaded = true
                    } catch (_: IllegalArgumentException) {
                        destination.delete()
                        errorStatus = HttpStatusCode.BadRequest
                        errorBody = mapOf("error" to mapOf("code" to "image_too_large", "message" to "Image is too large"))
                        part.dispose()
                        continue
                    } catch (_: Throwable) {
                        destination.delete()
                        errorStatus = HttpStatusCode.InternalServerError
                        errorBody = mapOf("error" to mapOf("code" to "internal_error", "message" to "Failed to store image"))
                        part.dispose()
                        continue
                    }
                }
                part.dispose()
            }

            val resolvedErrorStatus = errorStatus
            val resolvedErrorBody = errorBody
            if (resolvedErrorStatus != null && resolvedErrorBody != null) {
                call.respond(resolvedErrorStatus, resolvedErrorBody)
                return@post
            }

            val resolvedUploadUrl = uploadedUrl
            if (resolvedUploadUrl == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to mapOf("code" to "bad_request", "message" to "Image file is required"))
                )
                return@post
            }

            call.respond(UploadImageResponse(url = resolvedUploadUrl))
        }
    }
}

private fun resolveFileExtension(originalFileName: String?, contentType: ContentType?): String {
    val allowed = setOf("jpg", "jpeg", "png", "webp", "gif")
    val fromName = originalFileName
        ?.substringAfterLast('.', "")
        ?.trim()
        ?.lowercase()
        ?.takeIf { it in allowed }
    if (fromName != null) return ".$fromName"

    return when (contentType?.withoutParameters()?.toString()) {
        "image/jpeg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        else -> ".jpg"
    }
}
