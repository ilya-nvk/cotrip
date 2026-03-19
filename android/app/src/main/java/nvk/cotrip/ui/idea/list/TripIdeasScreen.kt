package nvk.cotrip.ui.idea.list

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripCard
import nvk.cotrip.ui.components.CoTripFab
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.idea.common.IdeaDayPickerSheet
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.BorderStrong
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.Success
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary

private const val KEY_BOTTOM_SPACER = "bottom_spacer"
private const val KEY_EMPTY_STATE = "empty_state"
private const val KEY_AI_BUTTON = "ai_button"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TripIdeasScreen(
    viewModel: TripIdeasViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(TripIdeasEvent.OnAutoRefresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is TripIdeasEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }

    val dayPicker = (state as? TripIdeasState.Content)?.dayPicker
    if (dayPicker != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(TripIdeasEvent.OnDismissDayPicker) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            IdeaDayPickerSheet(
                days = dayPicker.days,
                onSelect = { viewModel.onEvent(TripIdeasEvent.OnDaySelected(it)) }
            )
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
                        onClick = { viewModel.onEvent(TripIdeasEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.ideas_title),
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
            CoTripFab(onClick = { viewModel.onEvent(TripIdeasEvent.OnAddIdeaClick) })
        }
    ) { padding ->
        when (val uiState = state) {
            TripIdeasState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is TripIdeasState.Content -> {
                val pullRefreshState = rememberPullRefreshState(
                    refreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.onEvent(TripIdeasEvent.OnUserRefresh) },
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .pullRefresh(pullRefreshState)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = CoTripTokens.spacing.x2,
                            vertical = CoTripTokens.spacing.x2
                        ),
                        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
                    ) {
                        if (uiState.ideas.isEmpty()) {
                            item(key = KEY_EMPTY_STATE) {
                                EmptyIdeasState(
                                    onGetAiSuggestions = {
                                        viewModel.onEvent(TripIdeasEvent.OnGetAiSuggestionsClick)
                                    }
                                )
                            }
                        } else {
                            items(uiState.ideas, key = { it.id }) { idea ->
                                IdeaCard(
                                    idea = idea,
                                    onClick = { viewModel.onEvent(TripIdeasEvent.OnIdeaClick(idea.id)) },
                                    onAddToItinerary = {
                                        viewModel.onEvent(TripIdeasEvent.OnAddToItineraryClick(idea.id))
                                    }
                                )
                            }
                            item(key = KEY_AI_BUTTON) {
                                PrimaryButton(
                                    text = stringResource(R.string.ideas_get_ai_suggestions),
                                    onClick = { viewModel.onEvent(TripIdeasEvent.OnGetAiSuggestionsClick) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        item(key = KEY_BOTTOM_SPACER) {
                            Spacer(Modifier.height(96.dp))
                        }
                    }
                    PullRefreshIndicator(
                        refreshing = uiState.isRefreshing,
                        state = pullRefreshState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyIdeasState(
    onGetAiSuggestions: () -> Unit,
) {
    CoTripCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, Border),
        contentPadding = PaddingValues(CoTripTokens.spacing.x2)
    ) {
        Text(
            text = stringResource(R.string.ideas_empty_placeholder),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Spacer(Modifier.height(CoTripTokens.spacing.x2))

        PrimaryButton(
            text = stringResource(R.string.ideas_get_ai_suggestions),
            onClick = onGetAiSuggestions,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun IdeaCard(
    idea: IdeaListItemUi,
    onClick: () -> Unit,
    onAddToItinerary: () -> Unit,
) {
    CoTripCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = BorderStroke(1.dp, Border),
        contentPadding = PaddingValues(CoTripTokens.spacing.x2)
    ) {
        Text(
            text = idea.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(CoTripTokens.spacing.x1))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IdeaMetaItem(
                icon = CoTripIcons.Location,
                text = idea.city,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            idea.cost?.let {
                IdeaMetaItem(
                    icon = CoTripIcons.AccountBalance,
                    text = it,
                    maxLines = 1
                )
            }

            if (idea.commentsCount > 0) {
                IdeaMetaItem(
                    icon = CoTripIcons.Info,
                    text = stringResource(
                        R.string.ideas_comments_count,
                        idea.commentsCount
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(CoTripTokens.spacing.x1_5))

        if (idea.addedDay != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
            ) {
                Icon(
                    imageVector = CoTripIcons.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.ideas_added_to_day, idea.addedDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = Success
                )
            }
        } else {
            AddToItineraryButton(onClick = onAddToItinerary)
        }
    }
}

@Composable
private fun IdeaMetaItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

@Composable
private fun AddToItineraryButton(
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, BorderStrong),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.ideas_add_to_itinerary),
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary
        )
    }
}
