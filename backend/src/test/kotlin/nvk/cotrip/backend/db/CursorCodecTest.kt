package nvk.cotrip.backend.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CursorCodecTest {
    @Test
    fun given_rawCursorString_when_encodeThenDecode_then_roundTripsToSameValue() {
        // GIVEN
        val raw = "2026-03-16T12:34:56Z|trip|8da9b5e2-6fe5-4b5f-b4d3-22f8b2fed111"

        // WHEN
        val encoded = CursorCodec.encode(raw)
        val decoded = CursorCodec.decode(encoded)

        // THEN
        assertEquals(raw, decoded)
    }

    @Test
    fun given_invalidBase64Cursor_when_decode_then_throwsIllegalArgumentExceptionWithInvalidCursorMessage() {
        // GIVEN
        val invalidCursor = "!!!not-base64!!!"

        // WHEN / THEN
        val ex = assertFailsWith<IllegalArgumentException> {
            CursorCodec.decode(invalidCursor)
        }
        assertEquals("invalid_cursor", ex.message)
    }
}
