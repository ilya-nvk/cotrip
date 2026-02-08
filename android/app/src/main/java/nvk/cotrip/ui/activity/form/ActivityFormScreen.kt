package nvk.cotrip.ui.activity.form

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import nvk.cotrip.ui.components.CoTripDivider
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.DestructiveOutlinedButton
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.components.SecondaryButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.PrimaryBlue
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary

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

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ActivityFormEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
            }
        }
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

            state.headerText?.let {
                Text(
                    text = it,
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
                        onClick = { viewModel.onEvent(ActivityFormEvent.OnPickDateClick) }
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
                        onClick = { viewModel.onEvent(ActivityFormEvent.OnPickTimeClick) }
                    )
                }
            )

            CoTripDivider()

            SectionLabel(text = stringResource(R.string.activity_form_section_location_optional))
            Column(
                verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)
            ) {
                FormTextField(
                    value = state.locationName,
                    onValueChange = { viewModel.onEvent(ActivityFormEvent.OnLocationNameChange(it)) },
                    placeholder = stringResource(R.string.activity_form_location_name_placeholder),
                    singleLine = true,
                    trailingIcon = null
                )

                FormTextField(
                    value = state.locationLink,
                    onValueChange = { viewModel.onEvent(ActivityFormEvent.OnLocationLinkChange(it)) },
                    placeholder = stringResource(R.string.activity_form_location_link_placeholder),
                    singleLine = true,
                    trailingIcon = null
                )
            }

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

            SectionLabel(text = stringResource(R.string.activity_form_section_website_optional))
            FormTextField(
                value = state.website,
                onValueChange = { viewModel.onEvent(ActivityFormEvent.OnWebsiteChange(it)) },
                placeholder = stringResource(R.string.activity_form_website_placeholder),
                singleLine = true,
                trailingIcon = null
            )

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