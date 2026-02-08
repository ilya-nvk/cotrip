package nvk.cotrip.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import nvk.cotrip.backend.config.AppConfig
import nvk.cotrip.backend.routes.healthRoutes
import nvk.cotrip.backend.routes.v1.authRoutes
import nvk.cotrip.backend.routes.v1.inviteRoutes
import nvk.cotrip.backend.routes.v1.notImplementedRoutes
import nvk.cotrip.backend.routes.v1.tripRoutes
import nvk.cotrip.backend.routes.v1.userRoutes
import nvk.cotrip.backend.ws.commentsWebSocket

fun Application.configureRouting(appConfig: AppConfig) {
    install(Routing)
    routing {
        healthRoutes()
        authRoutes()
        userRoutes()
        tripRoutes()
        inviteRoutes(appConfig)
        notImplementedRoutes()
        commentsWebSocket()
    }
}
