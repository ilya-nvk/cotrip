package nvk.cotrip.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.BorderStrong

@Composable
fun CoTripDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Border)
}

@Composable
fun CoTripStrongDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = BorderStrong)
}

@Composable
fun CoTripDashedDivider(modifier: Modifier = Modifier) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(1.dp)) {
        drawLine(
            color = Border,
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(size.width, 0f),
            strokeWidth = 1f,
            pathEffect = dash
        )
    }
}
