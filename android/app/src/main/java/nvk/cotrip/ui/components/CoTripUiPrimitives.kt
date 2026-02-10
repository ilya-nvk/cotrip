package nvk.cotrip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import nvk.cotrip.ui.theme.Error
import nvk.cotrip.ui.theme.TextDark
import nvk.cotrip.ui.theme.TextDisabled

@Composable
fun CoTripIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = if (enabled) TextDark else TextDisabled
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .then(modifier)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun DestructiveIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CoTripIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tint = if (enabled) Error else TextDisabled
    )
}

@Composable
fun AvatarsStack(avatars: List<AvatarStackItem>, size: Dp) {
    Row {
        avatars.forEachIndexed { index, item ->
            Box(
                modifier = Modifier
                    .size(size)
                    .offset(x = (-6 * index).dp)
                    .zIndex((avatars.size - index).toFloat())
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!item.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(avatarColorFromInitials(item.initials)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.initials,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextDark
                        )
                    }
                }
            }
        }
    }
}

data class AvatarStackItem(
    val initials: String,
    val photoUrl: String? = null,
)
