package nvk.cotrip.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.BorderStrong
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.Error
import nvk.cotrip.ui.theme.PrimaryBlue
import nvk.cotrip.ui.theme.TextDisabled
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.WhiteCards

@Composable
fun CoTripTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val isError = !errorText.isNullOrBlank()
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(CoTripTokens.radius.medium)

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            maxLines = maxLines,
            isError = isError,
            label = label?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            visualTransformation = visualTransformation,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = BorderStrong,
                disabledBorderColor = Border,
                errorBorderColor = Error,

                focusedLabelColor = TextSecondary,
                unfocusedLabelColor = TextSecondary,
                errorLabelColor = Error,

                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                disabledTextColor = TextDisabled,
                errorTextColor = TextPrimary,

                cursorColor = PrimaryBlue,

                focusedPlaceholderColor = TextSecondary,
                unfocusedPlaceholderColor = TextSecondary,
                disabledPlaceholderColor = TextDisabled,

                focusedContainerColor = WhiteCards,
                unfocusedContainerColor = WhiteCards,
                disabledContainerColor = WhiteCards,
                errorContainerColor = WhiteCards,
            )
        )

        when {
            isError -> Text(
                text = errorText!!,
                style = MaterialTheme.typography.bodySmall,
                color = Error
            )
            !helperText.isNullOrBlank() -> Text(
                text = helperText,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
