package nvk.cotrip.ui.tripdetails

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class WeatherDayUi(
    val label: String,
    val temp: String,
    val icon: ImageVector,
    val tint: Color,
)
