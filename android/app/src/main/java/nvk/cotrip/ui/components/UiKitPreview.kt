package nvk.cotrip.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTheme
import nvk.cotrip.ui.theme.Info
import nvk.cotrip.ui.theme.Success
import nvk.cotrip.ui.theme.TextSecondary

@Preview(showBackground = true)
@Composable
fun UiKitPreview() {
    CoTripTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CoTripIconButton(
                    icon = CoTripIcons.Settings,
                    contentDescription = null,
                    onClick = {})
                CoTripIconButton(
                    icon = CoTripIcons.ArrowBack,
                    contentDescription = null,
                    onClick = {})
                DestructiveIconButton(
                    icon = CoTripIcons.Delete,
                    contentDescription = null,
                    onClick = {})
                CoTripIconButton(
                    icon = CoTripIcons.Settings,
                    contentDescription = null,
                    onClick = {},
                    enabled = false
                )
            }

            // Text fields
            CoTripTextField(value = "", onValueChange = {}, label = "Label")
            CoTripTextField(
                value = "",
                onValueChange = {},
                label = "With helper text",
                helperText = "Helper text goes here"
            )
            CoTripTextField(
                value = "Disabled",
                onValueChange = {},
                label = "Disabled",
                enabled = false
            )
            CoTripTextField(
                value = "Error state",
                onValueChange = {},
                label = "Error",
                errorText = "Error message here"
            )
            CoTripTextField(
                value = "Multiline\nText",
                onValueChange = {},
                label = "Multiline",
                singleLine = false,
                maxLines = 5
            )

            // Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Planned", CoTripIcons.Schedule, Info)
                StatusChip("Paid", CoTripIcons.CheckCircle, Success)
                StatusChip("Settled", CoTripIcons.AccountBalance, TextSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("Active", selected = true, onClick = {})
                FilterChip("All trips", selected = false, onClick = {})
                FilterChip("Past", selected = false, onClick = {})
            }

            // Dividers
            CoTripDivider()
            CoTripStrongDivider()
            CoTripDashedDivider()
        }
    }
}
