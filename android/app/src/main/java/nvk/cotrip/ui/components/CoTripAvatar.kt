package nvk.cotrip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import nvk.cotrip.ui.theme.TextDark

@Composable
fun CoTripAvatar(
    initials: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 40.dp,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    textColor: Color = TextDark,
    fallbackBackground: Color = avatarColorFromInitials(initials),
) {
    val normalizedPhotoUrl = photoUrl?.trim()?.takeIf { it.isNotEmpty() }
    val displayInitials = initials.trim().ifBlank { "?" }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(fallbackBackground),
        contentAlignment = Alignment.Center
    ) {
        if (normalizedPhotoUrl != null) {
            AsyncImage(
                model = normalizedPhotoUrl,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = displayInitials,
                style = textStyle,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}
