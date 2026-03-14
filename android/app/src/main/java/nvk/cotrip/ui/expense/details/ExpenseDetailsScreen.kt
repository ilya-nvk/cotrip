package nvk.cotrip.ui.expense.details

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripCard
import nvk.cotrip.ui.components.CoTripDivider
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.PrimaryBlue
import nvk.cotrip.ui.theme.Success
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.Warning
import nvk.cotrip.ui.theme.WarningText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailsScreen(
    viewModel: ExpenseDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(ExpenseDetailsEvent.OnRefresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExpenseDetailsEffect.ShowToastRes ->
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
                        onClick = { viewModel.onEvent(ExpenseDetailsEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.expense_details_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                },
                actions = {
                    CoTripIconButton(
                        icon = CoTripIcons.Edit,
                        contentDescription = null,
                        onClick = { viewModel.onEvent(ExpenseDetailsEvent.OnEditClick) }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            val uiState = state as? ExpenseDetailsState.Content ?: return@Scaffold
            when (uiState.status) {
                ExpenseDetailsStatus.Planned -> BottomPrimaryAction(
                    text = stringResource(R.string.expense_details_mark_as_paid),
                    onClick = { viewModel.onEvent(ExpenseDetailsEvent.OnMarkAsPaidClick) }
                )

                ExpenseDetailsStatus.Unsettled -> BottomPrimaryAction(
                    text = stringResource(R.string.expense_details_mark_all_settled),
                    onClick = { viewModel.onEvent(ExpenseDetailsEvent.OnMarkAllSettledClick) }
                )

                ExpenseDetailsStatus.Settled -> Unit
            }
        }
    ) { padding ->
        when (val uiState = state) {
            ExpenseDetailsState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ExpenseDetailsState.Content -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CoTripTokens.spacing.x2),
                    verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
                ) {
                    Spacer(Modifier.height(CoTripTokens.spacing.x1))

                    HeaderCard(state = uiState)

                    SectionTitle(text = stringResource(R.string.expense_details_split_section))

                    SplitDetailsCard(
                        state = uiState,
                        onMarkPaid = {
                            viewModel.onEvent(
                                ExpenseDetailsEvent.OnMarkParticipantPaidClick(
                                    it
                                )
                            )
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.splitType,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.expense_details_total, uiState.total),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                    }

                    val note = uiState.note
                    if (!note.isNullOrBlank()) {
                        SectionTitle(text = stringResource(R.string.expense_details_note_section))
                        CoTripCard(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Border),
                            contentPadding = PaddingValues(CoTripTokens.spacing.x2)
                        ) {
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(Modifier.height(96.dp))
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(state: ExpenseDetailsState.Content) {
    CoTripCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Border),
        contentPadding = PaddingValues(CoTripTokens.spacing.x2)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            StatusChip(status = state.status)
        }

        Spacer(Modifier.height(CoTripTokens.spacing.x1_5))

        Text(
            text = state.amount,
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary
        )

        if (state.paidBy != null && state.date != null) {
            Spacer(Modifier.height(CoTripTokens.spacing.x2))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.expense_details_paid_by),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
                Text(
                    text = state.paidBy,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = CoTripTokens.spacing.x1)
                )
            }

            Spacer(Modifier.height(CoTripTokens.spacing.x1))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.expense_details_date),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary
                )
                Text(
                    text = state.date,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = CoTripTokens.spacing.x1)
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: ExpenseDetailsStatus) {
    val background = when (status) {
        ExpenseDetailsStatus.Planned -> Warning
        ExpenseDetailsStatus.Unsettled -> Warning
        ExpenseDetailsStatus.Settled -> Success.copy(alpha = 0.18f)
    }
    val contentColor = when (status) {
        ExpenseDetailsStatus.Planned -> WarningText
        ExpenseDetailsStatus.Unsettled -> WarningText
        ExpenseDetailsStatus.Settled -> Success
    }
    val label = when (status) {
        ExpenseDetailsStatus.Planned -> stringResource(R.string.expense_details_status_planned)
        ExpenseDetailsStatus.Unsettled -> stringResource(R.string.expense_details_status_unsettled)
        ExpenseDetailsStatus.Settled -> stringResource(R.string.expense_details_status_settled)
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
        ) {
            Icon(
                imageVector = if (status == ExpenseDetailsStatus.Settled) CoTripIcons.CheckCircle else CoTripIcons.Schedule,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary
    )
}

@Composable
private fun SplitDetailsCard(
    state: ExpenseDetailsState.Content,
    onMarkPaid: (String) -> Unit,
) {
    CoTripCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Border),
        contentPadding = PaddingValues(0.dp)
    ) {
        state.splitRows.forEachIndexed { index, row ->
            SplitRow(
                row = row,
                status = state.status,
                onMarkPaid = { onMarkPaid(row.id) }
            )
            if (index != state.splitRows.lastIndex) {
                CoTripDivider()
            }
        }
    }
}

@Composable
private fun SplitRow(
    row: ExpenseSplitRowUi,
    status: ExpenseDetailsStatus,
    onMarkPaid: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = CoTripTokens.spacing.x1_5,
                vertical = CoTripTokens.spacing.x1_5
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Warning.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = row.initials,
                style = MaterialTheme.typography.titleMedium,
                color = PrimaryBlue
            )
        }

        Text(
            text = row.name,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = row.amount,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )

        when {
            status == ExpenseDetailsStatus.Planned -> Unit
            row.isPaid -> {
                Spacer(Modifier.width(CoTripTokens.spacing.x0_5))
                Icon(
                    imageVector = CoTripIcons.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(22.dp)
                )
            }

            else -> {
                TextButton(onClick = onMarkPaid) {
                    Text(
                        text = stringResource(R.string.expense_details_mark_paid),
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomPrimaryAction(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(
                horizontal = CoTripTokens.spacing.x2,
                vertical = CoTripTokens.spacing.x2
            )
    ) {
        PrimaryButton(
            text = text,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
