package nvk.cotrip.ui.trip.form

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.common.appUiLocale
import nvk.cotrip.ui.components.CoTripDashedDivider
import nvk.cotrip.ui.components.CoTripDropdownField
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.CoTripStrongDivider
import nvk.cotrip.ui.components.CoTripTextField
import nvk.cotrip.ui.components.DestructiveOutlinedButton
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.components.TertiaryTextButton
import nvk.cotrip.ui.theme.BorderStrong
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        onEvent(TripFormEvent.OnCoverPicked(uri?.toString()))
    }

    LaunchedEffect(effects) {
        effects.collectLatest { effect ->
            when (effect) {
                is TripFormEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
                TripFormEffect.OpenImagePicker -> imagePickerLauncher.launch("image/*")
            }
        }
    }

    fun showDatePicker(
        initialDate: LocalDate,
        minDate: LocalDate? = null,
        maxDate: LocalDate? = null,
        onSelected: (LocalDate) -> Unit,
    ) {
        val dialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                onSelected(LocalDate.of(year, month + 1, dayOfMonth))
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        )
        minDate?.let { dialog.datePicker.minDate = it.toEpochMillisAtStartOfDay() }
        maxDate?.let { dialog.datePicker.maxDate = it.toEpochMillisAtStartOfDay() }
        dialog.show()
    }

    state.limitDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { onEvent(TripFormEvent.OnDismissLimitDialog) },
            title = { Text(text = stringResource(R.string.limit_reached_dialog_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.limit_reached_dialog_message,
                        dialog.oldestLabel?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.limit_reached_oldest_fallback)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { onEvent(TripFormEvent.OnConfirmDeleteOldestAndRetry) }) {
                    Text(text = stringResource(R.string.limit_reached_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(TripFormEvent.OnDismissLimitDialog) }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    TripFormScreen(
        title = stringResource(titleRes),
        primaryButtonText = stringResource(primaryButtonRes),
        showAdvanced = showAdvanced,
        state = state,
        onEvent = onEvent,
        onStartDateClick = {
            val today = LocalDate.now()
            val minStartDate = TripDateRules.minStartDate(today)
            val maxStartDate = TripDateRules.maxStartDate(today)
            val initial = (state.startDate ?: today).clamp(minStartDate, maxStartDate)
            showDatePicker(
                initialDate = initial,
                minDate = minStartDate,
                maxDate = maxStartDate,
            ) { date ->
                onEvent(TripFormEvent.OnStartDateSelected(date))
            }
        },
        onEndDateClick = {
            val start = state.startDate ?: LocalDate.now()
            val minEndDate = start
            val maxEndDate = TripDateRules.maxEndDateFor(start)
            val initial = (state.endDate ?: minEndDate).clamp(minEndDate, maxEndDate)
            showDatePicker(
                initialDate = initial,
                minDate = minEndDate,
                maxDate = maxEndDate,
            ) { date ->
                onEvent(TripFormEvent.OnEndDateSelected(date))
            }
        },
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
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
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
                    .navigationBarsPadding()
                    .padding(
                        horizontal = CoTripTokens.spacing.x2,
                        vertical = CoTripTokens.spacing.x2
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PrimaryButton(
                    text = primaryButtonText,
                    onClick = { onEvent(TripFormEvent.OnPrimaryActionClick) },
                    enabled = state.canSubmit && !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                state.inlineErrorRes?.let { inlineError ->
                    Spacer(Modifier.height(CoTripTokens.spacing.x1))
                    Text(
                        text = stringResource(inlineError),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(CoTripTokens.spacing.x2))

                TertiaryTextButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { onEvent(TripFormEvent.OnCancelClick) },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
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
                    coverUrl = state.coverPreviewUri ?: state.coverUri,
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
                        onClick = onStartDateClick,
                        modifier = Modifier.weight(1f)
                    )
                    DateField(
                        label = stringResource(R.string.trip_form_end_date_label),
                        valueText = formatDateOrPlaceholder(state.endDate),
                        onClick = onEndDateClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            state.startDateErrorRes?.let { startDateError ->
                item {
                    Text(
                        text = stringResource(startDateError),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            state.endDateErrorRes?.let { endDateError ->
                item {
                    Text(
                        text = stringResource(endDateError),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
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
                    DestructiveOutlinedButton(
                        text = stringResource(R.string.trip_form_delete),
                        onClick = { onEvent(TripFormEvent.OnDeleteClick) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = CoTripTokens.spacing.x1),
                        leadingIcon = {
                            Icon(
                                imageVector = CoTripIcons.Delete,
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            item(key = KEY_SPACER) { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CoverPickerCard(
    coverUrl: String?,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(180.dp)
            .border(
                width = 1.dp,
                color = BorderStrong,
                shape = RoundedCornerShape(CoTripTokens.radius.large)
            )
            .clip(RoundedCornerShape(CoTripTokens.radius.large))
            .clickable(onClick = onPick)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(CoTripTokens.radius.large)),
                contentScale = ContentScale.Crop
            )
        } else {
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = CoTripIcons.PhotoCamera,
                    contentDescription = null,
                    tint = TextSecondary
                )
                Spacer(Modifier.height(CoTripTokens.spacing.x1))
                Text(
                    text = stringResource(R.string.trip_form_add_cover),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
            }
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
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    ) {
        OutlinedTextField(
            value = valueText,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = CoTripIcons.Schedule,
                    contentDescription = null,
                    tint = TextSecondary
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CoTripTokens.radius.medium),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BorderStrong,
                unfocusedBorderColor = BorderStrong,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                disabledTextColor = TextPrimary,
                disabledLabelColor = TextSecondary,
                disabledTrailingIconColor = TextSecondary,
                disabledBorderColor = BorderStrong,
            )
        )
    }
}

@Composable
private fun formatDateOrPlaceholder(date: LocalDate?): String {
    if (date == null) return stringResource(R.string.trip_form_date_placeholder)
    return DateTimeFormatter.ofPattern("dd.MM.yyyy", appUiLocale()).format(date)
}

private fun LocalDate.toEpochMillisAtStartOfDay(): Long {
    return atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun LocalDate.clamp(min: LocalDate, max: LocalDate): LocalDate {
    return when {
        isBefore(min) -> min
        isAfter(max) -> max
        else -> this
    }
}
