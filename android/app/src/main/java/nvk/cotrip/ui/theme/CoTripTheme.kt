package nvk.cotrip.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = WhiteCards,

    secondary = PrimaryBlue,
    onSecondary = WhiteCards,

    background = Background,
    onBackground = TextPrimary,

    surface = WhiteCards,
    onSurface = TextPrimary,

    surfaceVariant = SectionBackground,
    onSurfaceVariant = TextMedium,

    outline = Border,
    outlineVariant = BorderStrong,

    error = Error,
    onError = WhiteCards
)

val LocalCoTripExtraColors = staticCompositionLocalOf { CoTripExtraColors() }

@Composable
fun CoTripTheme(content: @Composable () -> Unit) {
    ProvideCoTripTokens {
        CompositionLocalProvider(
            LocalCoTripExtraColors provides CoTripExtraColors()
        ) {
            MaterialTheme(
                colorScheme = LightColorScheme,
                typography = CoTripTypography,
                shapes = CoTripShapes,
                content = content
            )
        }
    }
}

object CoTripThemeExt {
    val extraColors: CoTripExtraColors
        @Composable get() = LocalCoTripExtraColors.current
}
