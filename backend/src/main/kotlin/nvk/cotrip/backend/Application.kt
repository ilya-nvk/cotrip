package nvk.cotrip.backend

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopping
import io.ktor.server.application.ServerReady
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import nvk.cotrip.backend.auth.JwtService
import nvk.cotrip.backend.config.AppConfig
import nvk.cotrip.backend.config.loadConfig
import nvk.cotrip.backend.db.DatabaseFactory
import nvk.cotrip.backend.plugins.configureAuth
import nvk.cotrip.backend.plugins.configureLogging
import nvk.cotrip.backend.plugins.configureRouting
import nvk.cotrip.backend.plugins.configureSerialization
import nvk.cotrip.backend.plugins.configureStatusPages
import nvk.cotrip.backend.plugins.configureWebSockets

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    val appConfig: AppConfig = loadConfig(environment.config)

    DatabaseFactory.init(appConfig.db)
    JwtService.init(appConfig.jwt)

    configureLogging()
    configureSerialization()
    configureStatusPages()
    configureWebSockets()
    configureAuth(appConfig.jwt)
    configureRouting()

    environment.monitor.subscribe(ApplicationStarted) {
        log.info("CoTrip backend started")
    }
    environment.monitor.subscribe(ServerReady) {
        log.info("CoTrip backend ready")
    }
    environment.monitor.subscribe(ApplicationStopping) {
        log.info("CoTrip backend stopping")
    }
}
