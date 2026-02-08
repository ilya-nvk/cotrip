package nvk.cotrip.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class CoTripRadius(
    val small: Dp = 4.dp,
    val medium: Dp = 18.dp,
    val large: Dp = 18.dp,
    val xLarge: Dp = 18.dp
)

@Immutable
data class CoTripSpacing(
    val x0_5: Dp = 4.dp,
    val x1: Dp = 8.dp,
    val x1_5: Dp = 12.dp,
    val x2: Dp = 16.dp,
    val x2_5: Dp = 20.dp,
    val x3: Dp = 24.dp,
    val x4: Dp = 32.dp,
)

@Immutable
data class CoTripElevation(
    val cardHover: Dp = 2.dp,
    val fabElevated: Dp = 4.dp,
    val phoneFrame: Dp = 4.dp
)

val LocalRadius = staticCompositionLocalOf { CoTripRadius() }
val LocalSpacing = staticCompositionLocalOf { CoTripSpacing() }
val LocalElevation = staticCompositionLocalOf { CoTripElevation() }

object CoTripTokens {
    val radius: CoTripRadius
        @Composable get() = LocalRadius.current
    val spacing: CoTripSpacing
        @Composable get() = LocalSpacing.current
    val elevation: CoTripElevation
        @Composable get() = LocalElevation.current
}

@Composable
fun ProvideCoTripTokens(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalRadius provides CoTripRadius(),
        LocalSpacing provides CoTripSpacing(),
        LocalElevation provides CoTripElevation(),
        content = content
    )
}
