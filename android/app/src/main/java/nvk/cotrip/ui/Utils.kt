package nvk.cotrip.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import nvk.cotrip.ui.theme.TextDark
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

@Composable
fun AvatarsStack(initials: List<String>, size: Dp) {
    Row {
        initials.forEachIndexed { index, it ->
            Box(
                modifier = Modifier
                    .size(size)
                    .offset(x = (-6 * index).dp)
                    .zIndex((initials.size - index).toFloat())
                    .clip(CircleShape)
                    .background(avatarColorFromInitials(it)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextDark
                )
            }
        }
    }
}
