package nvk.cotrip.ui.expense.list

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripCard
import nvk.cotrip.ui.components.CoTripFab
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.PrimaryLight
import nvk.cotrip.ui.theme.Success
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.WarningText

private const val KEY_SUMMARY = "summary"
private const val KEY_SPENT_HEADER = "spent_header"
private const val KEY_PLANNED_HEADER = "planned_header"
private const val KEY_BOTTOM_SPACER = "bottom_spacer"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripExpensesScreen(
    viewModel: TripExpensesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(TripExpensesEvent.OnRefresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel, context) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TripExpensesEffect.ShowToastRes ->
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
                        onClick = { viewModel.onEvent(TripExpensesEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.expenses_title),
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
        floatingActionButton = {
            CoTripFab(onClick = { viewModel.onEvent(TripExpensesEvent.OnAddExpenseClick) })
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
            item(key = KEY_SUMMARY) {
                SummaryBlock(summary = state.summary)
            }

            item(key = KEY_SPENT_HEADER) {
                SectionTitle(text = stringResource(R.string.expenses_spent_section))
            }

            items(state.spent, key = { it.id }) { expense ->
                ExpenseCard(
                    expense = expense,
                    onClick = { viewModel.onEvent(TripExpensesEvent.OnExpenseClick(expense.id)) }
                )
            }

            item(key = KEY_PLANNED_HEADER) {
                SectionTitle(text = stringResource(R.string.expenses_planned_section))
            }

            items(state.planned, key = { it.id }) { expense ->
                ExpenseCard(
                    expense = expense,
                    onClick = { viewModel.onEvent(TripExpensesEvent.OnExpenseClick(expense.id)) }
                )
            }

            item(key = KEY_BOTTOM_SPACER) {
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun SummaryBlock(
    summary: ExpenseSummaryUi,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)) {
        CoTripCard(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Border)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(PrimaryLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = CoTripIcons.AccountBalance,
                        contentDescription = null,
                        tint = TextPrimary
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)) {
                    Text(
                        text = stringResource(R.string.expenses_total_spent),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = summary.totalSpent,
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, Success.copy(alpha = 0.35f)),
                color = Success.copy(alpha = 0.12f)
            ) {
                Column(
                    modifier = Modifier.padding(CoTripTokens.spacing.x2),
                    verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
                ) {
                    Text(
                        text = stringResource(R.string.expenses_your_balance),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = stringResource(
                            R.string.expenses_balance_amount,
                            summary.balanceLabel,
                            summary.balanceAmount
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = Success
                    )
                }
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, Border),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier.padding(CoTripTokens.spacing.x2),
                    verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
                ) {
                    Text(
                        text = stringResource(R.string.expenses_total_planned),
                        style = MaterialTheme.typography.titleSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = summary.totalPlanned,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
            }
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
private fun ExpenseCard(
    expense: ExpenseListItemUi,
    onClick: () -> Unit,
) {
    CoTripCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = BorderStroke(1.dp, Border),
        contentPadding = PaddingValues(CoTripTokens.spacing.x2)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
        ) {
            Text(
                text = expense.title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = expense.amount,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        }

        Spacer(Modifier.height(CoTripTokens.spacing.x1))

        Text(
            text = expense.paidBy,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(Modifier.height(CoTripTokens.spacing.x1_5))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Border.copy(alpha = 0.35f)
            ) {
                Text(
                    text = expense.splitType,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.width(CoTripTokens.spacing.x1))

            SettlementText(settlement = expense.settlement)
        }
    }
}

@Composable
private fun SettlementText(settlement: ExpenseSettlementUi) {
    when (settlement) {
        is ExpenseSettlementUi.OwedToYou -> {
            Text(
                text = stringResource(R.string.expenses_status_owed_to_you, settlement.amount),
                style = MaterialTheme.typography.titleMedium,
                color = Success,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        is ExpenseSettlementUi.YouOwe -> {
            Text(
                text = stringResource(R.string.expenses_status_you_owe, settlement.amount),
                style = MaterialTheme.typography.titleMedium,
                color = WarningText,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        ExpenseSettlementUi.Settled -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = CoTripIcons.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(CoTripTokens.spacing.x0_5))
                Text(
                    text = stringResource(R.string.expenses_status_settled),
                    style = MaterialTheme.typography.titleMedium,
                    color = Success
                )
            }
        }

        ExpenseSettlementUi.Planned -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = CoTripIcons.Schedule,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(CoTripTokens.spacing.x0_5))
                Text(
                    text = stringResource(R.string.expenses_status_planned),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
