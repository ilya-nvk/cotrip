package nvk.cotrip.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nvk.cotrip.R
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
            CoTripTextField(
                value = "",
                onValueChange = {},
                label = stringResource(R.string.ui_kit_preview_label)
            )
            CoTripTextField(
                value = "",
                onValueChange = {},
                label = stringResource(R.string.ui_kit_preview_with_helper),
                helperText = stringResource(R.string.ui_kit_preview_helper_text)
            )
            CoTripTextField(
                value = stringResource(R.string.ui_kit_preview_disabled_value),
                onValueChange = {},
                label = stringResource(R.string.ui_kit_preview_disabled_label),
                enabled = false
            )
            CoTripTextField(
                value = stringResource(R.string.ui_kit_preview_error_state),
                onValueChange = {},
                label = stringResource(R.string.ui_kit_preview_error_label),
                errorText = stringResource(R.string.ui_kit_preview_error_message)
            )
            CoTripTextField(
                value = stringResource(R.string.ui_kit_preview_multiline_value),
                onValueChange = {},
                label = stringResource(R.string.ui_kit_preview_multiline_label),
                singleLine = false,
                maxLines = 5
            )

            // Chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(
                    stringResource(R.string.expense_form_status_planned),
                    CoTripIcons.Schedule,
                    Info
                )
                StatusChip(
                    stringResource(R.string.expense_form_status_paid),
                    CoTripIcons.CheckCircle,
                    Success
                )
                StatusChip(
                    stringResource(R.string.expenses_status_settled),
                    CoTripIcons.AccountBalance,
                    TextSecondary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(stringResource(R.string.section_active), selected = true, onClick = {})
                FilterChip(
                    stringResource(R.string.ui_kit_preview_all_trips),
                    selected = false,
                    onClick = {})
                FilterChip(
                    stringResource(R.string.ui_kit_preview_past),
                    selected = false,
                    onClick = {})
            }

            // Dividers
            CoTripDivider()
            CoTripStrongDivider()
            CoTripDashedDivider()
        }
    }
}
