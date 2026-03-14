package nvk.cotrip.ui.idea.form

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.CoTripListItem
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.BorderStrong
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.Error
import nvk.cotrip.ui.theme.PrimaryBlue
import nvk.cotrip.ui.theme.PrimaryLight
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary

@Composable
fun CreateIdeaScreen(
    viewModel: CreateIdeaViewModel = hiltViewModel(),
) {
    IdeaFormScreen(viewModel = viewModel)
}

@Composable
fun EditIdeaScreen(
    viewModel: EditIdeaViewModel = hiltViewModel(),
) {
    IdeaFormScreen(viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeaFormScreen(
    viewModel: IdeaFormContract,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is IdeaFormEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }

    val isPrimaryEnabled = state.title.isNotBlank() && !state.isSaving

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
                        onClick = { viewModel.onEvent(IdeaFormEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(
                            if (state.mode == IdeaFormMode.Create)
                                R.string.idea_form_title_add
                            else
                                R.string.idea_form_title_edit
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
                    verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PrimaryButton(
                        text = stringResource(R.string.idea_form_primary_save),
                        onClick = { viewModel.onEvent(IdeaFormEvent.OnPrimaryClick) },
                        enabled = isPrimaryEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.mode == IdeaFormMode.Edit) {
                        TextButton(
                            onClick = { viewModel.onEvent(IdeaFormEvent.OnDeleteClick) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Error)
                        ) {
                            Text(text = stringResource(R.string.idea_form_delete))
                        }
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

            FieldLabel(text = stringResource(R.string.idea_form_title_label))
            FormTextField(
                value = state.title,
                onValueChange = { viewModel.onEvent(IdeaFormEvent.OnTitleChange(it)) },
                placeholder = stringResource(R.string.idea_form_title_placeholder),
                singleLine = true
            )

            FieldLabel(text = stringResource(R.string.idea_form_city_label))
            Column(verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)) {
                FormTextField(
                    value = state.city,
                    onValueChange = { viewModel.onEvent(IdeaFormEvent.OnCityChange(it)) },
                    placeholder = stringResource(R.string.idea_form_city_placeholder),
                    singleLine = true,
                )
                if (state.isCitySearching) {
                    Text(
                        text = stringResource(R.string.idea_form_city_searching),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                state.citySuggestions.take(6).forEach { suggestion ->
                    CoTripListItem(
                        title = suggestion.fullText,
                        onClick = { viewModel.onEvent(IdeaFormEvent.OnCitySelected(suggestion)) }
                    )
                }
            }

            FieldLabel(text = stringResource(R.string.idea_form_link_label))
            FormTextField(
                value = state.link,
                onValueChange = { viewModel.onEvent(IdeaFormEvent.OnLinkChange(it)) },
                placeholder = stringResource(R.string.idea_form_link_placeholder),
                singleLine = true
            )

            FieldLabel(text = stringResource(R.string.idea_form_cost_label))
            Column(verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)) {
                FormTextField(
                    value = state.costAmount,
                    onValueChange = { viewModel.onEvent(IdeaFormEvent.OnCostAmountChange(it)) },
                    placeholder = stringResource(R.string.idea_form_cost_placeholder),
                    singleLine = true,
                    leadingIcon = {
                        Text(
                            text = state.currencySymbol,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                )

                CostTypeToggle(
                    selected = state.costType,
                    onSelect = { viewModel.onEvent(IdeaFormEvent.OnCostTypeChange(it)) }
                )
            }

            FieldLabel(text = stringResource(R.string.idea_form_notes_label))
            FormTextField(
                value = state.notes,
                onValueChange = { viewModel.onEvent(IdeaFormEvent.OnNotesChange(it)) },
                placeholder = stringResource(R.string.idea_form_notes_placeholder),
                singleLine = false,
                minLines = 4
            )

            Spacer(Modifier.height(90.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary
    )
}

@Composable
private fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    minLines: Int = 1,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .let { base ->
                if (onClick != null) base.clickable(onClick = onClick) else base
            },
        shape = MaterialTheme.shapes.large,
        singleLine = singleLine,
        minLines = minLines,
        readOnly = readOnly,
        placeholder = {
            if (placeholder.isNotBlank()) {
                Text(
                    text = placeholder,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

@Composable
private fun CostTypeToggle(
    selected: IdeaCostType,
    onSelect: (IdeaCostType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
    ) {
        CostTypeButton(
            text = stringResource(R.string.idea_form_cost_per_person),
            selected = selected == IdeaCostType.PerPerson,
            onClick = { onSelect(IdeaCostType.PerPerson) },
            modifier = Modifier.weight(1f)
        )
        CostTypeButton(
            text = stringResource(R.string.idea_form_cost_total),
            selected = selected == IdeaCostType.Total,
            onClick = { onSelect(IdeaCostType.Total) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CostTypeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) PrimaryLight else Color.Transparent
    val contentColor = if (selected) PrimaryBlue else TextPrimary
    val borderColor = if (selected) PrimaryBlue else BorderStrong

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(text = text)
    }
}
