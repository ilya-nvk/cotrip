package nvk.cotrip.backend.ws

import io.ktor.server.websocket.DefaultWebSocketServerSession
import java.util.concurrent.ConcurrentHashMap

object CommentsHub {
    private val sessionsByTrip = ConcurrentHashMap<String, MutableSet<DefaultWebSocketServerSession>>()

    fun add(tripId: String, session: DefaultWebSocketServerSession) {
        val set = sessionsByTrip.computeIfAbsent(tripId) { ConcurrentHashMap.newKeySet() }
        set.add(session)
    }

    fun remove(tripId: String, session: DefaultWebSocketServerSession) {
        sessionsByTrip[tripId]?.remove(session)
        if (sessionsByTrip[tripId]?.isEmpty() == true) {
            sessionsByTrip.remove(tripId)
        }
    }

    fun sessions(tripId: String): Set<DefaultWebSocketServerSession> =
        sessionsByTrip[tripId]?.toSet() ?: emptySet()
}
