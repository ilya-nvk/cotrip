package nvk.cotrip.ui.expense.form

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import nvk.cotrip.ui.components.CoTripDivider
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
import nvk.cotrip.ui.theme.Success
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.Warning
import nvk.cotrip.ui.theme.WarningText
import java.util.Locale
import kotlin.math.abs

@Composable
fun CreateExpenseScreen(
    viewModel: CreateExpenseViewModel = hiltViewModel(),
) {
    ExpenseFormScreen(viewModel = viewModel)
}

@Composable
fun EditExpenseScreen(
    viewModel: EditExpenseViewModel = hiltViewModel(),
) {
    ExpenseFormScreen(viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseFormScreen(
    viewModel: ExpenseFormContract,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ExpenseFormEffect.ShowToastRes -> {
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    if (state.paidByPickerVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(ExpenseFormEvent.OnDismissPaidByPicker) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PaidByPickerSheet(
                participants = state.participants,
                onSelect = { viewModel.onEvent(ExpenseFormEvent.OnPaidBySelected(it)) }
            )
        }
    }

    val amountValue = parseMoney(state.amount)
    val selectedParticipants = state.participants.filter { it.isSelected }
    val selectedPaidBy = state.participants.firstOrNull { it.id == state.paidById }?.name

    val customSum = selectedParticipants.sumOf { parseMoney(it.customAmount) }
    val remaining = amountValue - customSum
    val isCustomBalanced = abs(remaining) < 0.01

    val isPrimaryEnabled = state.title.isNotBlank() &&
            amountValue > 0.0 &&
            selectedParticipants.isNotEmpty() &&
            (state.status == ExpenseFormStatus.Planned || state.paidById != null) &&
            (state.splitType == ExpenseSplitType.SplitEqually || isCustomBalanced) &&
            !state.isSaving

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
                        onClick = { viewModel.onEvent(ExpenseFormEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(
                            if (state.mode == ExpenseFormMode.Create) {
                                R.string.expense_form_title_add
                            } else {
                                R.string.expense_form_title_edit
                            }
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
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)
                ) {
                    PrimaryButton(
                        text = stringResource(R.string.expense_form_primary_save),
                        onClick = { viewModel.onEvent(ExpenseFormEvent.OnPrimaryClick) },
                        enabled = isPrimaryEnabled,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.mode == ExpenseFormMode.Edit) {
                        TextButton(
                            onClick = { viewModel.onEvent(ExpenseFormEvent.OnDeleteClick) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Error)
                        ) {
                            Text(text = stringResource(R.string.expense_form_delete))
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

            FieldLabel(text = stringResource(R.string.expense_form_title_label))
            FormTextField(
                value = state.title,
                onValueChange = { viewModel.onEvent(ExpenseFormEvent.OnTitleChange(it)) },
                placeholder = stringResource(R.string.expense_form_title_placeholder),
                singleLine = true
            )

            FieldLabel(text = stringResource(R.string.expense_form_amount_label))
            FormTextField(
                value = state.amount,
                onValueChange = { viewModel.onEvent(ExpenseFormEvent.OnAmountChange(it)) },
                placeholder = stringResource(R.string.expense_form_amount_placeholder),
                singleLine = true,
                leadingIcon = {
                    Text(
                        text = state.currencySymbol,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            )

            FieldLabel(text = stringResource(R.string.expense_form_status_label))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
            ) {
                ToggleButton(
                    text = stringResource(R.string.expense_form_status_planned),
                    selected = state.status == ExpenseFormStatus.Planned,
                    onClick = { viewModel.onEvent(ExpenseFormEvent.OnStatusChange(ExpenseFormStatus.Planned)) },
                    modifier = Modifier.weight(1f)
                )
                ToggleButton(
                    text = stringResource(R.string.expense_form_status_paid),
                    selected = state.status == ExpenseFormStatus.Paid,
                    onClick = { viewModel.onEvent(ExpenseFormEvent.OnStatusChange(ExpenseFormStatus.Paid)) },
                    modifier = Modifier.weight(1f)
                )
            }

            FieldLabel(text = stringResource(R.string.expense_form_paid_by_label))
            FormTextField(
                value = selectedPaidBy.orEmpty(),
                onValueChange = {},
                placeholder = stringResource(R.string.expense_form_paid_by_placeholder),
                singleLine = true,
                readOnly = true,
                trailingIcon = {
                    CoTripIconButton(
                        icon = CoTripIcons.ExpandMore,
                        contentDescription = null,
                        onClick = { viewModel.onEvent(ExpenseFormEvent.OnPaidByClick) }
                    )
                },
                onClick = { viewModel.onEvent(ExpenseFormEvent.OnPaidByClick) }
            )

            FieldLabel(text = stringResource(R.string.expense_form_date_label))
            FormTextField(
                value = state.dateText,
                onValueChange = {},
                placeholder = stringResource(R.string.expense_form_date_placeholder),
                singleLine = true,
                readOnly = true,
                trailingIcon = {
                    CoTripIconButton(
                        icon = CoTripIcons.Calendar,
                        contentDescription = null,
                        onClick = { viewModel.onEvent(ExpenseFormEvent.OnDateClick) }
                    )
                },
                onClick = { viewModel.onEvent(ExpenseFormEvent.OnDateClick) }
            )

            CoTripDivider(modifier = Modifier.padding(top = CoTripTokens.spacing.x1))

            FieldLabel(text = stringResource(R.string.expense_form_split_between_label))
            Column(verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)) {
                state.participants.forEach { participant ->
                    ParticipantCheckRow(
                        participant = participant,
                        onChecked = {
                            viewModel.onEvent(
                                ExpenseFormEvent.OnParticipantChecked(
                                    participantId = participant.id,
                                    checked = it
                                )
                            )
                        }
                    )
                }
            }

            FieldLabel(text = stringResource(R.string.expense_form_split_type_label))
            Column(verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)) {
                SplitTypeRow(
                    text = stringResource(R.string.expense_form_split_equally),
                    selected = state.splitType == ExpenseSplitType.SplitEqually,
                    onClick = {
                        viewModel.onEvent(
                            ExpenseFormEvent.OnSplitTypeChange(
                                ExpenseSplitType.SplitEqually
                            )
                        )
                    }
                )
                SplitTypeRow(
                    text = stringResource(R.string.expense_form_custom_amounts),
                    selected = state.splitType == ExpenseSplitType.CustomAmounts,
                    onClick = {
                        viewModel.onEvent(
                            ExpenseFormEvent.OnSplitTypeChange(
                                ExpenseSplitType.CustomAmounts
                            )
                        )
                    }
                )
            }

            if (state.splitType == ExpenseSplitType.CustomAmounts) {
                FieldLabel(text = stringResource(R.string.expense_form_amount_per_person))
                selectedParticipants.forEach { participant ->
                    CustomAmountRow(
                        participant = participant,
                        currencySymbol = state.currencySymbol,
                        onValueChange = {
                            viewModel.onEvent(
                                ExpenseFormEvent.OnCustomAmountChange(
                                    participantId = participant.id,
                                    value = it
                                )
                            )
                        }
                    )
                }

                val remainingColor = if (isCustomBalanced) Success else WarningText
                val remainingBackground = if (isCustomBalanced) {
                    Success.copy(alpha = 0.12f)
                } else {
                    Warning.copy(alpha = 0.55f)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, remainingColor.copy(alpha = 0.35f)),
                    color = remainingBackground
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(CoTripTokens.spacing.x2),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.expense_form_remaining),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextSecondary
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = formatMoney(remaining),
                            style = MaterialTheme.typography.headlineMedium,
                            color = remainingColor
                        )
                    }
                }
            }

            FieldLabel(text = stringResource(R.string.expense_form_note_label))
            FormTextField(
                value = state.note,
                onValueChange = { viewModel.onEvent(ExpenseFormEvent.OnNoteChange(it)) },
                placeholder = stringResource(R.string.expense_form_note_placeholder),
                singleLine = false,
                minLines = 4
            )

            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = TextPrimary
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
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base },
        shape = MaterialTheme.shapes.large,
        singleLine = singleLine,
        minLines = minLines,
        readOnly = readOnly,
        placeholder = {
            Text(
                text = placeholder,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
private fun ToggleButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, if (selected) PrimaryBlue else BorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) PrimaryLight else Color.Transparent,
            contentColor = if (selected) PrimaryBlue else TextPrimary
        )
    ) {
        Text(text = text)
    }
}

@Composable
private fun ParticipantCheckRow(
    participant: ExpenseParticipantUi,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
    ) {
        Checkbox(
            checked = participant.isSelected,
            onCheckedChange = { onChecked(it) }
        )

        Avatar(initials = participant.initials)

        Text(
            text = participant.name,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
    }
}

@Composable
private fun SplitTypeRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
    }
}

@Composable
private fun CustomAmountRow(
    participant: ExpenseParticipantUi,
    currencySymbol: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
    ) {
        Avatar(initials = participant.initials)

        Text(
            text = participant.name,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )

        OutlinedTextField(
            value = participant.customAmount,
            onValueChange = onValueChange,
            modifier = Modifier.width(140.dp),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            leadingIcon = {
                Text(
                    text = currencySymbol,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = BorderStrong,
                unfocusedIndicatorColor = Border,
                cursorColor = PrimaryBlue
            )
        )
    }
}

@Composable
private fun Avatar(initials: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(Warning.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            color = PrimaryBlue
        )
    }
}

@Composable
private fun PaidByPickerSheet(
    participants: List<ExpenseParticipantUi>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = CoTripTokens.spacing.x2,
                vertical = CoTripTokens.spacing.x2
            ),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)
    ) {
        Text(
            text = stringResource(R.string.expense_form_paid_by_picker_title),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )

        participants.forEach { participant ->
            CoTripListItem(
                title = participant.name,
                onClick = { onSelect(participant.id) }
            )
        }
    }
}

private fun parseMoney(value: String): Double {
    return value.replace(',', '.').toDoubleOrNull() ?: 0.0
}

private fun formatMoney(value: Double): String {
    return String.format(Locale.US, "€%.2f", value)
}
