package nvk.cotrip.ui.idea.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripDivider
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary

@Composable
fun IdeaDayPickerSheet(
    days: List<IdeaDayOptionUi>,
    onSelect: (IdeaDayOptionUi) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = CoTripTokens.spacing.x2,
                vertical = CoTripTokens.spacing.x2
            ),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
    ) {
        Text(
            text = stringResource(R.string.ideas_pick_day_title),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentPadding = PaddingValues(vertical = CoTripTokens.spacing.x1),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(days, key = { it.id }) { day ->
                DayOptionRow(day = day, onClick = { onSelect(day) })
                CoTripDivider()
            }
        }

        Spacer(Modifier.height(CoTripTokens.spacing.x1))
    }
}

@Composable
private fun DayOptionRow(
    day: IdeaDayOptionUi,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                horizontal = CoTripTokens.spacing.x1,
                vertical = CoTripTokens.spacing.x1_5
            ),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
    ) {
        Text(
            text = stringResource(R.string.ideas_pick_day_label, day.dayNumber, day.dateText),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Text(
            text = day.city,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
