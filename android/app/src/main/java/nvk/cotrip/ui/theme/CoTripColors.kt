package nvk.cotrip.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Primary
val PrimaryBlue = Color(0xFF1976D2)
val PrimaryDark = Color(0xFF1565C0)
val PrimaryLight = Color(0xFFE3F2FD)

// Neutrals (text)
val TextPrimary = Color(0xFF212121)
val TextDark = Color(0xFF424242)
val TextMedium = Color(0xFF616161)
val TextSecondary = Color(0xFF757575)
val TextDisabled = Color(0xFF9E9E9E)

// Backgrounds
val WhiteCards = Color(0xFFFFFFFF)
val Background = Color(0xFFFAFAFA)
val SectionBackground = Color(0xFFF5F5F5)
val Canvas = Color(0xFFE8E8E8)

// Borders
val Border = Color(0xFFE0E0E0)
val BorderStrong = Color(0xFFBDBDBD)
val BorderWarning = Color(0xFFFFB74D)

// Semantic
val Success = Color(0xFF4CAF50)
val Error = Color(0xFFF44336)
val Warning = Color(0xFFFFF3E0)
val Info = Color(0xFF2196F3)

//Text
val WarningText = Color(0xFFE65100)

@Immutable
data class CoTripExtraColors(
    val canvas: Color = Canvas,
    val sectionBackground: Color = SectionBackground,
    val border: Color = Border,
    val borderStrong: Color = BorderStrong,
    val primaryLight: Color = PrimaryLight,
    val primaryDark: Color = PrimaryDark,
    val success: Color = Success,
    val warning: Color = Warning,
    val info: Color = Info,
)
