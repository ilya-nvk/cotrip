package nvk.cotrip.ui.forecast

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class ForecastDayUi(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val temp: String,
    val description: String,
)