package nvk.cotrip.ui.itinerary

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripCard
import nvk.cotrip.ui.components.CoTripFab
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.CoTripListItem
import nvk.cotrip.ui.components.CoTripTextField
import nvk.cotrip.ui.components.TertiaryTextButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary

private const val KEY_HEADER = "header"
private const val KEY_BOTTOM_SPACER = "bottom_spacer"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TripItineraryScreen(
    viewModel: TripItineraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(TripItineraryEvent.OnAutoRefresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is TripItineraryEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }

    val currentState = state.cityPicker
    if (currentState != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(TripItineraryEvent.OnDismissCityPicker) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CityPickerSheet(
                query = currentState.query,
                cities = currentState.suggestions,
                isSearching = currentState.isSearching,
                onQueryChange = { viewModel.onEvent(TripItineraryEvent.OnCityQueryChange(it)) },
                onSelect = { viewModel.onEvent(TripItineraryEvent.OnCitySelected(it)) }
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
                        onClick = { viewModel.onEvent(TripItineraryEvent.OnBackClick) }
                    )
                },
                actions = {
                    val hasReorderable = state.days.any { it.activities.size > 1 }
                    if (state.mode == ItineraryMode.Filled && hasReorderable) {
                        CoTripIconButton(
                            icon = if (state.isReordering) CoTripIcons.CheckCircle else CoTripIcons.Reorder,
                            contentDescription = stringResource(
                                if (state.isReordering)
                                    R.string.itinerary_reorder_done
                                else
                                    R.string.itinerary_reorder_start
                            ),
                            onClick = { viewModel.onEvent(TripItineraryEvent.OnToggleReorder) }
                        )
                    }
                },
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)) {
                        Text(
                            text = stringResource(R.string.itinerary_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = state.dateRange,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (state.mode == ItineraryMode.Filled) {
                CoTripFab(onClick = { viewModel.onEvent(TripItineraryEvent.OnAddActivityClick) })
            }
        }
    ) { padding ->
        val pullRefreshState = rememberPullRefreshState(
            refreshing = state.isRefreshing,
            onRefresh = { viewModel.onEvent(TripItineraryEvent.OnUserRefresh) }
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
                item(key = KEY_HEADER) {
                    Spacer(Modifier.height(0.dp))
                }

                when (state.mode) {
                    ItineraryMode.Filled -> {
                        items(state.days, key = { it.id }) { day ->
                            FilledDaySection(
                                day = day,
                                isReordering = state.isReordering,
                                onChooseCity = {
                                    viewModel.onEvent(
                                        TripItineraryEvent.OnChooseCityClick(day.id)
                                    )
                                },
                                onActivityClick = {
                                    viewModel.onEvent(
                                        TripItineraryEvent.OnActivityClick(it)
                                    )
                                },
                                onReorderMove = { fromIndex, toIndex ->
                                    viewModel.onEvent(
                                        TripItineraryEvent.OnReorderMove(
                                            dayId = day.id,
                                            fromIndex = fromIndex,
                                            toIndex = toIndex
                                        )
                                    )
                                },
                                onReorderCommit = {
                                    viewModel.onEvent(TripItineraryEvent.OnReorderCommit(day.id))
                                },
                            )
                        }
                    }

                    ItineraryMode.Empty -> {
                        items(state.days, key = { it.id }) { day ->
                            EmptyDayCard(
                                day = day,
                                onChooseCity = {
                                    viewModel.onEvent(
                                        TripItineraryEvent.OnChooseCityClick(
                                            dayId = day.id
                                        )
                                    )
                                }
                            )
                        }
                    }
                }

                item(key = KEY_BOTTOM_SPACER) {
                    Spacer(Modifier.height(96.dp))
                }
            }
            PullRefreshIndicator(
                refreshing = state.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun FilledDaySection(
    day: ItineraryDayUi,
    isReordering: Boolean,
    onChooseCity: () -> Unit,
    onActivityClick: (String) -> Unit,
    onReorderMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onReorderCommit: () -> Unit,
) {
    val density = LocalDensity.current
    val fallbackRowHeight = with(density) { 72.dp.toPx() }
    var rowHeightPx by remember(day.id) { mutableFloatStateOf(fallbackRowHeight) }
    var draggingId by remember(day.id) { mutableStateOf<String?>(null) }
    var draggingIndex by remember(day.id) { mutableIntStateOf(-1) }
    var dragOffset by remember(day.id) { mutableFloatStateOf(0f) }

    Column(verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoTripTokens.spacing.x0_5),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.itinerary_day_title, day.dayNumber),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Text(
                text = " · ",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            if (day.city != null) {
                TertiaryTextButton(
                    text = day.city,
                    onClick = onChooseCity
                )
                Icon(
                    imageVector = CoTripIcons.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            } else {
                TertiaryTextButton(
                    text = stringResource(R.string.itinerary_choose_city),
                    onClick = onChooseCity
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = day.dateText,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        CoTripCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp)
        ) {
            day.activities.forEachIndexed { index, activity ->
                val isDragging = draggingId == activity.id
                ActivityRow(
                    activity = activity,
                    isReordering = isReordering,
                    isDragging = isDragging,
                    dragOffset = if (isDragging) dragOffset else 0f,
                    onMeasure = { height ->
                        if (height > 0) rowHeightPx = height.toFloat()
                    },
                    onDragStart = {
                        draggingId = activity.id
                        draggingIndex = index
                        dragOffset = 0f
                    },
                    onDrag = { delta ->
                        if (draggingIndex == -1) return@ActivityRow
                        dragOffset += delta
                        val step = rowHeightPx.takeIf { it > 0f } ?: fallbackRowHeight
                        val shift = (dragOffset / step).toInt()
                        val target = (draggingIndex + shift).coerceIn(0, day.activities.lastIndex)
                        if (target != draggingIndex) {
                            onReorderMove(draggingIndex, target)
                            dragOffset -= (target - draggingIndex) * step
                            draggingIndex = target
                        }
                    },
                    onDragEnd = {
                        if (draggingId != null) {
                            draggingId = null
                            draggingIndex = -1
                            dragOffset = 0f
                            onReorderCommit()
                        }
                    },
                    onClick = { onActivityClick(activity.id) },
                )
                if (index != day.activities.lastIndex) {
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun EmptyDayCard(
    day: ItineraryDayUi,
    onChooseCity: () -> Unit,
) {
    CoTripCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = CoTripTokens.spacing.x2,
            vertical = CoTripTokens.spacing.x2
        ),
        border = BorderStrokeCompat()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.itinerary_day_title, day.dayNumber),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Text(
                text = " · ",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )

            TertiaryTextButton(
                text = stringResource(R.string.itinerary_choose_city),
                onClick = onChooseCity
            )
        }

        Spacer(Modifier.height(CoTripTokens.spacing.x0_5))

        Text(
            text = day.dateText,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(Modifier.height(CoTripTokens.spacing.x2))

        Text(
            text = stringResource(R.string.itinerary_empty_day_hint),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
    }
}

@Composable
private fun ActivityRow(
    activity: ItineraryActivityUi,
    isReordering: Boolean,
    isDragging: Boolean,
    dragOffset: Float,
    onMeasure: (height: Int) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (delta: Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
) {
    val rowModifier = if (isReordering) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.fillMaxWidth().clickable { onClick() }
    }

    Row(
        modifier = rowModifier
            .graphicsLayer { translationY = dragOffset }
            .zIndex(if (isDragging) 1f else 0f)
            .pointerInput(isReordering) {
                if (!isReordering) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    }
                )
            }
            .onSizeChanged { onMeasure(it.height) }
            .padding(
                horizontal = CoTripTokens.spacing.x2,
                vertical = CoTripTokens.spacing.x1_5
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = activity.timeText,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(56.dp)
        )

        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Border)
        )

        Spacer(Modifier.width(CoTripTokens.spacing.x1_5))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
        ) {
            Text(
                text = activity.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (activity.subtitle != null) {
                Text(
                    text = activity.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (activity.priceText != null) {
            Spacer(Modifier.width(CoTripTokens.spacing.x1))
            Text(
                text = activity.priceText,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        if (isReordering) {
            Spacer(Modifier.width(CoTripTokens.spacing.x1))
            Icon(
                imageVector = CoTripIcons.Reorder,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.CenterVertically)
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = CoTripTokens.spacing.x2)
            .background(Border)
    )
}

@Composable
private fun CityPickerSheet(
    query: String,
    cities: List<CitySuggestionUi>,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (CitySuggestionUi) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = CoTripTokens.spacing.x2,
                vertical = CoTripTokens.spacing.x2
            ),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
    ) {
        Text(
            text = stringResource(R.string.itinerary_choose_city_title),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )

        CoTripTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.itinerary_choose_city_placeholder),
            modifier = Modifier.fillMaxWidth()
        )

        if (isSearching) {
            Text(
                text = stringResource(R.string.itinerary_choose_city_searching),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentPadding = PaddingValues(vertical = CoTripTokens.spacing.x1),
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
        ) {
            items(cities, key = { it.placeId ?: it.name }) { city ->
                CoTripListItem(
                    title = city.fullText ?: city.name,
                    onClick = { onSelect(city) }
                )
            }
        }
    }
}

@Composable
private fun BorderStrokeCompat() =
    androidx.compose.foundation.BorderStroke(1.dp, Border)
