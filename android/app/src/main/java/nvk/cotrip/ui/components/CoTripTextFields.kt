package nvk.cotrip.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.BorderStrong
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
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 5,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    val isError = !errorText.isNullOrBlank()
    val shape = RoundedCornerShape(18.dp)

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            maxLines = maxLines,
            isError = isError,
            label = label?.let { { Text(it) } },
            placeholder = placeholder?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            textStyle = MaterialTheme.typography.bodyLarge,
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

        Spacer(Modifier.height(4.dp))

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
