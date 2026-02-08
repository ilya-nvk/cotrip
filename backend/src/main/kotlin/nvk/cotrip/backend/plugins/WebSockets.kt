package nvk.cotrip.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets

fun Application.configureWebSockets() {
    install(WebSockets) {
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}
