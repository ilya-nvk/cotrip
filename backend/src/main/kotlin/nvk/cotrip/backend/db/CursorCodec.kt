package nvk.cotrip.backend.db

import java.nio.charset.StandardCharsets
import java.util.Base64

object CursorCodec {
    fun encode(raw: String): String {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(cursor: String): String {
        return try {
            String(
                Base64.getUrlDecoder().decode(cursor),
                StandardCharsets.UTF_8
            )
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("invalid_cursor")
        }
    }
}
