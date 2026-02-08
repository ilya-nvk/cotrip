package nvk.cotrip.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

fun tripGradientFromId(id: String): Brush {
    val hash = abs(id.hashCode())

    val hueBase = (hash % 360).toFloat()
    val hueSecondary = (hueBase + 30f) % 360f

    val color1 = Color.hsv(
        hue = hueBase,
        saturation = 0.35f,
        value = 0.90f
    )

    val color2 = Color.hsv(
        hue = hueSecondary,
        saturation = 0.45f,
        value = 0.80f
    )

    return Brush.linearGradient(
        colors = listOf(color1, color2)
    )
}

fun avatarColorFromInitials(initials: String): Color {
    val hash = abs(initials.hashCode())

    val hue = (hash % 360).toFloat()
    val saturation = 0.1f
    val value = 0.90f

    return Color.hsv(
        hue = hue,
        saturation = saturation,
        value = value,
    )
}
