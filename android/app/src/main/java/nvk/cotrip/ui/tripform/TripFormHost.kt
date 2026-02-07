package nvk.cotrip.ui.tripform

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripDashedDivider
import nvk.cotrip.ui.components.CoTripDropdownField
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.CoTripStrongDivider
import nvk.cotrip.ui.components.CoTripTextField
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.components.TertiaryTextButton
import nvk.cotrip.ui.theme.BorderStrong
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val KEY_SPACER = "spacer"

@Composable
fun TripFormHost(
    titleRes: Int,
    primaryButtonRes: Int,
    showAdvanced: Boolean,
    state: TripFormState,
    effects: Flow<TripFormEffect>,
    onEvent: (TripFormEvent) -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is TripFormEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT).show()
            }
        }
    }

    TripFormScreen(
        title = stringResource(titleRes),
        primaryButtonText = stringResource(primaryButtonRes),
        showAdvanced = showAdvanced,
        state = state,
        onEvent = onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripFormScreen(
    title: String,
    primaryButtonText: String,
    showAdvanced: Boolean,
    state: TripFormState,
    onEvent: (TripFormEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    CoTripIconButton(
                        icon = CoTripIcons.Close,
                        contentDescription = null,
                        onClick = { onEvent(TripFormEvent.OnCloseClick) }
                    )
                },
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CoTripTokens.spacing.x2, vertical = CoTripTokens.spacing.x2),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PrimaryButton(
                    text = primaryButtonText,
                    onClick = { onEvent(TripFormEvent.OnPrimaryActionClick) },
                    enabled = state.canSubmit && !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(CoTripTokens.spacing.x2))

                TertiaryTextButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { onEvent(TripFormEvent.OnCancelClick) },
                    enabled = !state.isLoading
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                horizontal = CoTripTokens.spacing.x2,
                vertical = CoTripTokens.spacing.x2
            ),
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
        ) {
            item {
                Text(
                    text = stringResource(R.string.trip_form_cover_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            item {
                CoverPickerCard(
                    onPick = { onEvent(TripFormEvent.OnPickCoverClick) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                CoTripTextField(
                    value = state.name,
                    onValueChange = { onEvent(TripFormEvent.OnNameChange(it)) },
                    label = stringResource(R.string.trip_form_name_label),
                    singleLine = true
                )
            }

            item {
                Text(
                    text = stringResource(R.string.trip_form_dates_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)) {
                    DateField(
                        label = stringResource(R.string.trip_form_start_date_label),
                        valueText = formatDateOrPlaceholder(state.startDate),
                        onClick = { onEvent(TripFormEvent.OnStartDateClick) },
                        modifier = Modifier.weight(1f)
                    )
                    DateField(
                        label = stringResource(R.string.trip_form_end_date_label),
                        valueText = formatDateOrPlaceholder(state.endDate),
                        onClick = { onEvent(TripFormEvent.OnEndDateClick) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                CoTripTextField(
                    value = state.description,
                    onValueChange = { onEvent(TripFormEvent.OnDescriptionChange(it)) },
                    label = stringResource(R.string.trip_form_description_label),
                    singleLine = false,
                    maxLines = 4
                )
            }

            item {
                CoTripDropdownField(
                    label = stringResource(R.string.trip_form_currency_label),
                    valueText = stringResource(state.currency.labelRes),
                    items = state.availableCurrencies,
                    onSelect = { onEvent(TripFormEvent.OnCurrencySelect(it)) },
                    itemText = { stringResource(it.labelRes) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                )
            }

            if (showAdvanced) {
                item {
                    Spacer(Modifier.height(CoTripTokens.spacing.x1))
                    CoTripStrongDivider()
                    Spacer(Modifier.height(CoTripTokens.spacing.x1))
                }

                item {
                    Text(
                        text = stringResource(R.string.trip_form_advanced_section),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = CoTripTokens.spacing.x1),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoTripIconButton(
                            icon = CoTripIcons.AccountBalance,
                            contentDescription = null,
                            onClick = { onEvent(TripFormEvent.OnArchiveClick) }
                        )
                        Spacer(Modifier.width(CoTripTokens.spacing.x1))
                        Text(
                            text = stringResource(R.string.trip_form_archive),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = CoTripTokens.spacing.x1),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CoTripIconButton(
                            icon = CoTripIcons.Delete,
                            contentDescription = null,
                            onClick = { onEvent(TripFormEvent.OnDeleteClick) }
                        )
                        Spacer(Modifier.width(CoTripTokens.spacing.x1))
                        Text(
                            text = stringResource(R.string.trip_form_delete),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item(key = KEY_SPACER) { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CoverPickerCard(
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(180.dp)
            .clip(RoundedCornerShape(CoTripTokens.radius.large))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = CoTripIcons.PhotoCamera,
                contentDescription = null,
                tint = TextSecondary
            )
            Spacer(Modifier.height(CoTripTokens.spacing.x1))
            TertiaryTextButton(
                text = stringResource(R.string.trip_form_add_cover),
                onClick = onPick
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(CoTripTokens.spacing.x1)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(CoTripTokens.radius.large))
                    .background(MaterialTheme.colorScheme.surface)
            )
            CoTripDashedDivider(modifier = Modifier.matchParentSize())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    valueText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = valueText,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                imageVector = CoTripIcons.Schedule,
                contentDescription = null,
                tint = TextSecondary
            )
        },
        modifier = modifier,
        shape = RoundedCornerShape(CoTripTokens.radius.medium),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BorderStrong,
            unfocusedBorderColor = BorderStrong,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
        )
    )
}

@Composable
private fun formatDateOrPlaceholder(date: LocalDate?): String {
    if (date == null) return stringResource(R.string.trip_form_date_placeholder)
    return DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault()).format(date)
}