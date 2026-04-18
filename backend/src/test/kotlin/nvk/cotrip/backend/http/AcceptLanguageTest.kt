package nvk.cotrip.backend.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AcceptLanguageTest {

    @Test
    fun preferredOpenWeatherUiLang_parses_en_and_ru() {
        assertEquals("en", preferredOpenWeatherUiLang("en-US"))
        assertEquals("en", preferredOpenWeatherUiLang("en-GB, ru;q=0.8"))
        assertEquals("ru", preferredOpenWeatherUiLang("ru-RU"))
        assertNull(preferredOpenWeatherUiLang("de-DE"))
        assertNull(preferredOpenWeatherUiLang(null))
        assertNull(preferredOpenWeatherUiLang(""))
    }
}
