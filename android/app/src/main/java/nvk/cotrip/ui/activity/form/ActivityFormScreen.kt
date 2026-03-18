package nvk.cotrip.ui.activity.form

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.common.appUiLocale
import nvk.cotrip.ui.components.CoTripDivider
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.CoTripListItem
import nvk.cotrip.ui.components.DestructiveOutlinedButton
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.components.SecondaryButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.PrimaryBlue
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CreateActivityScreen(
    viewModel: CreateActivityViewModel = hiltViewModel(),
) {
    ActivityFormScreen(viewModel = viewModel)
}

@Composable
fun EditActivityScreen(
    viewModel: EditActivityViewModel = hiltViewModel(),
) {
    ActivityFormScreen(viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityFormScreen(
    viewModel: ActivityFormContract,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", appUiLocale())

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ActivityFormEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }

    state.limitDialog?.let { dialog ->
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(ActivityFormEvent.OnDismissLimitDialog) },
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
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.onEvent(ActivityFormEvent.OnConfirmDeleteOldestAndRetry) }
                ) {
                    Text(text = stringResource(R.string.limit_reached_dialog_confirm))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.onEvent(ActivityFormEvent.OnDismissLimitDialog) }
                ) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    fun showDatePicker() {
        val minDate = state.tripStartDate
        val maxDate = state.tripEndDate
        val fallbackDate = minDate ?: LocalDate.now()
        val initialDate = runCatching { LocalDate.parse(state.dateText, dateFormatter) }
            .getOrNull()
            ?.coerceIn(minDate, maxDate)
            ?: fallbackDate
        val dialog = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                viewModel.onEvent(ActivityFormEvent.OnDateSelected(LocalDate.of(year, month + 1, dayOfMonth)))
            },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        )
        minDate?.let { dialog.datePicker.minDate = it.toEpochMillisAtStartOfDay() }
        maxDate?.let { dialog.datePicker.maxDate = it.toEpochMillisAtStartOfDay() }
        dialog.show()
    }

    fun showTimePicker() {
        val initialTime = runCatching {
            if (state.timeText.isBlank()) null else LocalTime.parse(state.timeText)
        }.getOrNull() ?: LocalTime.now()
        TimePickerDialog(
            context,
            { _, hour, minute ->
                viewModel.onEvent(ActivityFormEvent.OnTimeSelected(LocalTime.of(hour, minute)))
            },
            initialTime.hour,
            initialTime.minute,
            true
        ).show()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    CoTripIconButton(
                        icon = CoTripIcons.ArrowBack,
                        contentDescription = null,
                        onClick = { viewModel.onEvent(ActivityFormEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(
                            if (state.mode == ActivityFormMode.Create)
                                R.string.activity_form_title_add
                            else
                                R.string.activity_form_title_edit
                        ),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(
                        horizontal = CoTripTokens.spacing.x2,
                        vertical = CoTripTokens.spacing.x2
                    )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
                ) {
                    PrimaryButton(
                        text = stringResource(
                            if (state.mode == ActivityFormMode.Create)
                                R.string.activity_form_primary_add
                            else
                                R.string.activity_form_primary_save
                        ),
                        onClick = { viewModel.onEvent(ActivityFormEvent.OnPrimaryClick) },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.mode == ActivityFormMode.Edit) {
                        DestructiveOutlinedButton(
                            text = stringResource(R.string.activity_form_delete),
                            onClick = { viewModel.onEvent(ActivityFormEvent.OnDeleteClick) },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(
                                    imageVector = CoTripIcons.Delete,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CoTripTokens.spacing.x2),
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
        ) {
            Spacer(Modifier.height(CoTripTokens.spacing.x1))

            state.headerDayNumber?.let { dayNumber ->
                val headerCity = state.headerCity
                val headerText = if (headerCity.isNullOrBlank()) {
                    stringResource(R.string.itinerary_day_title, dayNumber)
                } else {
                    stringResource(R.string.ideas_pick_day_label, dayNumber, headerCity)
                }
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }

            SectionLabel(text = stringResource(R.string.activity_form_section_title))
            FormTextField(
                value = state.title,
                onValueChange = { viewModel.onEvent(ActivityFormEvent.OnTitleChange(it)) },
                placeholder = stringResource(R.string.activity_form_title_placeholder),
                singleLine = true,
                trailingIcon = null
            )

            CoTripDivider()

            SectionLabel(text = stringResource(R.string.activity_form_section_date))
            FormTextField(
                value = state.dateText,
                onValueChange = {},
                placeholder = "",
                singleLine = true,
                readOnly = true,
                trailingIcon = {
                    CoTripIconButton(
                        icon = CoTripIcons.Calendar,
                        contentDescription = null,
                        onClick = { showDatePicker() }
                    )
                }
            )

            CoTripDivider()

            SectionLabel(text = stringResource(R.string.activity_form_section_time_optional))
            FormTextField(
                value = state.timeText,
                onValueChange = {},
                placeholder = stringResource(R.string.activity_form_time_placeholder),
                singleLine = true,
                readOnly = true,
                trailingIcon = {
                    CoTripIconButton(
                        icon = CoTripIcons.Schedule,
                        contentDescription = null,
                        onClick = { showTimePicker() }
                    )
                }
            )

            CoTripDivider()

            SectionLabel(text = stringResource(R.string.activity_form_section_location_optional))
            Column(
                verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)
            ) {
                FormTextField(
                    value = state.locationInput,
                    onValueChange = { viewModel.onEvent(ActivityFormEvent.OnLocationInputChange(it)) },
                    placeholder = stringResource(R.string.activity_form_location_placeholder),
                    singleLine = true,
                    trailingIcon = null
                )

                if (state.isLocationSearching) {
                    Text(
                        text = stringResource(R.string.activity_form_location_searching),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                state.locationSuggestions.take(6).forEach { suggestion ->
                    CoTripListItem(
                        title = suggestion.fullText,
                        onClick = {
                            viewModel.onEvent(
                                ActivityFormEvent.OnLocationSuggestionSelected(
                                    suggestion
                                )
                            )
                        }
                    )
                }
            }

            CoTripDivider()

            SectionLabel(text = stringResource(R.string.activity_form_section_link_optional))
            FormTextField(
                value = state.linkInput,
                onValueChange = { viewModel.onEvent(ActivityFormEvent.OnLinkChange(it)) },
                placeholder = stringResource(R.string.activity_form_link_placeholder),
                singleLine = true,
                trailingIcon = null
            )

            CoTripDivider()

            SectionLabel(text = stringResource(R.string.activity_form_section_cost_optional))
            Column(
                verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)
            ) {
                FormTextField(
                    value = state.costAmount,
                    onValueChange = { viewModel.onEvent(ActivityFormEvent.OnCostAmountChange(it)) },
                    placeholder = stringResource(R.string.activity_form_cost_placeholder),
                    singleLine = true,
                    leadingIcon = {
                        Text(
                            text = state.currencySymbol,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                )

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)
                ) {
                    SecondaryButton(
                        text = stringResource(R.string.activity_form_cost_per_person),
                        onClick = { viewModel.onEvent(ActivityFormEvent.OnCostTypeChange(CostType.PerPerson)) },
                        modifier = Modifier.weight(1f),
                        enabled = state.costType != CostType.PerPerson
                    )
                    SecondaryButton(
                        text = stringResource(R.string.activity_form_cost_total),
                        onClick = { viewModel.onEvent(ActivityFormEvent.OnCostTypeChange(CostType.Total)) },
                        modifier = Modifier.weight(1f),
                        enabled = state.costType != CostType.Total
                    )
                }
            }

            CoTripDivider()

            SectionLabel(text = stringResource(R.string.activity_form_section_notes_optional))
            FormTextField(
                value = state.notes,
                onValueChange = { viewModel.onEvent(ActivityFormEvent.OnNotesChange(it)) },
                placeholder = stringResource(R.string.activity_form_notes_placeholder),
                singleLine = false,
                minLines = 4,
                trailingIcon = null
            )
        }
    }
}

private fun LocalDate.toEpochMillisAtStartOfDay(): Long {
    return atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun LocalDate.coerceIn(min: LocalDate?, max: LocalDate?): LocalDate {
    return when {
        min != null && isBefore(min) -> min
        max != null && isAfter(max) -> max
        else -> this
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary
    )
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    trailingIcon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    minLines: Int = 1,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        singleLine = singleLine,
        minLines = minLines,
        readOnly = readOnly,
        placeholder = {
            if (placeholder.isNotBlank()) {
                Text(text = placeholder, color = TextSecondary)
            }
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = PrimaryBlue,
            unfocusedIndicatorColor = Border,
            disabledIndicatorColor = Border,
            cursorColor = PrimaryBlue
        )
    )
}
