package nvk.cotrip.backend.routes

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import nvk.cotrip.backend.config.AppLinksConfig

@Serializable
private data class AssetLinksStatement(
    val relation: List<String>,
    val target: AssetLinksTarget,
)

@Serializable
private data class AssetLinksTarget(
    val namespace: String,
    @SerialName("package_name")
    val packageName: String,
    @SerialName("sha256_cert_fingerprints")
    val sha256CertFingerprints: List<String>,
)

fun Route.appLinksRoutes(appLinks: AppLinksConfig) {
    get("/.well-known/assetlinks.json") {
        val packageName = appLinks.androidPackage
        if (packageName.isNullOrBlank() || appLinks.sha256CertFingerprints.isEmpty()) {
            call.respond(emptyList<AssetLinksStatement>())
            return@get
        }

        val statement = AssetLinksStatement(
            relation = listOf("delegate_permission/common.handle_all_urls"),
            target = AssetLinksTarget(
                namespace = "android_app",
                packageName = packageName,
                sha256CertFingerprints = appLinks.sha256CertFingerprints
            )
        )
        call.respond(listOf(statement))
    }
}
