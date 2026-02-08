package nvk.cotrip.backend.ws

import kotlinx.serialization.json.Json

object WsJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
