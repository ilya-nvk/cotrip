package nvk.cotrip.ui.common

import java.util.Locale

/**
 * App locale policy:
 * - Russian when system locale is Russian
 * - English for all other locales
 */
fun appUiLocale(systemLocale: Locale = Locale.getDefault()): Locale {
    return if (systemLocale.language.equals("ru", ignoreCase = true)) {
        Locale("ru", "RU")
    } else {
        Locale.ENGLISH
    }
}
