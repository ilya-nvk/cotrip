package nvk.cotrip.ui.aisuggestions

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripCard
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.PrimaryBlue
import nvk.cotrip.ui.theme.Success
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary

private const val KEY_HEADER = "header"
private const val KEY_BOTTOM_SPACER = "bottom_spacer"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSuggestionsScreen(
    viewModel: RouteSuggestionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is RouteSuggestionsEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    CoTripIconButton(
                        icon = CoTripIcons.ArrowBack,
                        contentDescription = null,
                        onClick = { viewModel.onEvent(RouteSuggestionsEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.ai_suggestions_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                },
                actions = {
                    if (state is RouteSuggestionsState.Content) {
                        IconButton(onClick = { viewModel.onEvent(RouteSuggestionsEvent.OnRefreshClick) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = TextPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        when (val uiState = state) {
            is RouteSuggestionsState.Loading -> {
                LoadingState(modifier = Modifier.padding(padding))
            }

            is RouteSuggestionsState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(vertical = CoTripTokens.spacing.x2),
                    verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
                ) {
                    item(key = KEY_HEADER) {
                        SummaryHeader(
                            city = uiState.city,
                            subtitle = uiState.subtitle
                        )
                    }

                    items(uiState.suggestions, key = { it.id }) { suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            onSaveClick = {
                                viewModel.onEvent(RouteSuggestionsEvent.OnSaveClick(suggestion.id))
                            }
                        )
                    }

                    item(key = KEY_BOTTOM_SPACER) {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CoTripTokens.spacing.x2),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                strokeWidth = 5.dp,
                color = PrimaryBlue
            )
            Text(
                text = stringResource(R.string.ai_suggestions_loading),
                style = MaterialTheme.typography.headlineSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun SummaryHeader(
    city: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = CoTripTokens.spacing.x2),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = city,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Border)
        )
    }
}

@Composable
private fun SuggestionCard(
    suggestion: AiSuggestionItemUi,
    onSaveClick: () -> Unit,
) {
    CoTripCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoTripTokens.spacing.x2),
        border = BorderStroke(1.dp, Border),
        contentPadding = PaddingValues(CoTripTokens.spacing.x2)
    ) {
        Text(
            text = suggestion.title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )

        Spacer(Modifier.height(CoTripTokens.spacing.x1))

        Text(
            text = suggestion.description,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(Modifier.height(CoTripTokens.spacing.x1_5))

        Row(
            horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetaChip(icon = CoTripIcons.Info, text = suggestion.typeLabel)
            MetaChip(icon = CoTripIcons.Schedule, text = suggestion.durationLabel)
            MetaChip(icon = CoTripIcons.AccountBalance, text = suggestion.budgetLabel)
        }

        Spacer(Modifier.height(CoTripTokens.spacing.x1_5))

        Text(
            text = stringResource(R.string.ai_suggestions_estimated_cost, suggestion.estimatedCost),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(Modifier.height(CoTripTokens.spacing.x1_5))

        if (suggestion.isSaved) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = Success.copy(alpha = 0.18f),
                border = BorderStroke(1.dp, Success.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = CoTripTokens.spacing.x1_5),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = CoTripIcons.CheckCircle,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(CoTripTokens.spacing.x1))
                    Text(
                        text = stringResource(R.string.ai_suggestions_saved),
                        style = MaterialTheme.typography.labelLarge,
                        color = Success,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            PrimaryButton(
                text = stringResource(R.string.ai_suggestions_save_to_ideas),
                onClick = onSaveClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MetaChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = Border.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
