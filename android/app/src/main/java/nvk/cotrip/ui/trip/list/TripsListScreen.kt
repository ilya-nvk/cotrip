package nvk.cotrip.ui.trip.list

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.AvatarsStack
import nvk.cotrip.ui.components.CoTripFab
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.components.tripGradientFromId
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.PrimaryBlue
import nvk.cotrip.ui.theme.PrimaryLight
import nvk.cotrip.ui.theme.TextMedium
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary

private const val KEY_ACTIVE_HEADER = "active_header"
private const val KEY_UPCOMING_HEADER = "upcoming_header"
private const val KEY_UPCOMING_EMPTY_CARD = "upcoming_empty_card"
private const val KEY_PAST_HEADER = "past_header"
private const val KEY_SPACER = "spacer"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TripsListScreen(
    viewModel: TripsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val lazyColumnState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is TripsListEffect.ShowToast -> Toast.makeText(
                    context,
                    effect.message,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val shouldScrollToPastTrips =
        state is TripsListUiState.Content && (state as TripsListUiState.Content).showPastTrips
    LaunchedEffect(shouldScrollToPastTrips) {
        if (shouldScrollToPastTrips) {
            val index = lazyColumnState.calculatePastTripsHederIndex()
            index?.let { lazyColumnState.animateScrollToItem(it) }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.trips_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                },
                actions = {
                    CoTripIconButton(
                        icon = CoTripIcons.Settings,
                        contentDescription = stringResource(R.string.settings),
                        onClick = { viewModel.onEvent(TripsListEvent.OnSettingsClick) })
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            CoTripFab(onClick = { viewModel.onEvent(TripsListEvent.OnCreateTripClick) })
        },
    ) { padding ->
        when (val currentState = state) {
            is TripsListUiState.Loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            is TripsListUiState.Content -> {
                if (currentState.activeTrips.isEmpty() && currentState.pastTrips.isEmpty() && currentState.upcomingTrips.isEmpty()) {
                    EmptyTripsState(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(horizontal = CoTripTokens.spacing.x3),
                        onCreateTrip = { viewModel.onEvent(TripsListEvent.OnCreateTripClick) })
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(
                            horizontal = CoTripTokens.spacing.x2,
                            vertical = CoTripTokens.spacing.x1_5
                        ),
                        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2),
                        state = lazyColumnState,
                    ) {
                        if (currentState.activeTrips.isNotEmpty()) {
                            item(key = KEY_ACTIVE_HEADER) {
                                SectionHeader(text = stringResource(R.string.section_active))
                            }
                            items(currentState.activeTrips, key = { it.id }) { trip ->
                                TripCard(
                                    trip = trip,
                                    onClick = { viewModel.onEvent(TripsListEvent.OnTripClick(trip.id)) })
                            }
                        }

                        item(key = KEY_UPCOMING_HEADER) {
                            SectionHeader(text = stringResource(R.string.section_upcoming))
                        }

                        if (currentState.activeTrips.isEmpty() && currentState.upcomingTrips.isEmpty()) {
                            item(key = KEY_UPCOMING_EMPTY_CARD) {
                                UpcomingEmptyCard()
                            }
                        } else {
                            items(currentState.upcomingTrips, key = { it.id }) { trip ->
                                TripCard(
                                    trip = trip,
                                    onClick = { viewModel.onEvent(TripsListEvent.OnTripClick(trip.id)) })
                            }
                        }

                        if (currentState.pastTrips.isNotEmpty()) {
                            item(key = KEY_PAST_HEADER) {
                                PastHeader(
                                    count = currentState.pastTrips.size,
                                    expanded = currentState.showPastTrips,
                                    onToggle = { viewModel.onEvent(TripsListEvent.OnTogglePast) })
                            }
                            if (currentState.showPastTrips) items(
                                currentState.pastTrips,
                                key = { it.id }) { trip ->
                                TripCard(
                                    trip = trip, onClick = {
                                        viewModel.onEvent(
                                            TripsListEvent.OnTripClick(trip.id)
                                        )
                                    })
                            }
                        }

                        item(key = KEY_SPACER) {
                            Spacer(modifier = Modifier.height(62.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    modifier: Modifier = Modifier, text: String
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
        modifier = modifier.padding(horizontal = CoTripTokens.spacing.x0_5)
    )
}

@Composable
private fun PastHeader(
    modifier: Modifier = Modifier, count: Int, expanded: Boolean, onToggle: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CoTripTokens.spacing.x0_5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.section_past_with_count, count),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Spacer(Modifier.weight(1f))
        CoTripIconButton(
            icon = CoTripIcons.ExpandMore,
            contentDescription = stringResource(R.string.toggle_past),
            onClick = onToggle,
            modifier = Modifier.rotate(if (expanded) 180f else 0f)
        )
    }
}

@Composable
private fun TripCard(
    modifier: Modifier = Modifier, trip: TripCardUi, onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(CoTripTokens.radius.xLarge),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = CoTripTokens.elevation.cardHover,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(tripGradientFromId(trip.id))
            ) {
                if (trip.isInProgress) {
                    InProgressBadge(modifier = Modifier.padding(CoTripTokens.spacing.x1_5))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CoTripTokens.spacing.x2),
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = trip.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(CoTripTokens.spacing.x0_5))
                    Text(
                        text = trip.dateRange,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = trip.locationLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(CoTripTokens.spacing.x1_5))
                    PeopleRow(
                        initials = trip.initials, peopleCountText = trip.peopleCountText
                    )
                }

                Icon(
                    imageVector = CoTripIcons.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun InProgressBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(CoTripTokens.radius.large),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = CoTripTokens.spacing.x1_5, vertical = CoTripTokens.spacing.x0_5
            ), verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(CoTripTokens.spacing.x1))
            Text(
                text = stringResource(R.string.in_progress),
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun PeopleRow(
    initials: List<String>, peopleCountText: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarsStack(initials = initials.take(4), size = 28.dp)
        Spacer(Modifier.width(CoTripTokens.spacing.x0_5))
        Text(
            text = peopleCountText,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun UpcomingEmptyCard(
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(CoTripTokens.radius.xLarge),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, Border),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = CoTripTokens.spacing.x2, vertical = CoTripTokens.spacing.x3
            ), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.no_upcoming_trips),
                style = MaterialTheme.typography.titleMedium,
                color = TextMedium
            )
            Spacer(Modifier.height(CoTripTokens.spacing.x1))
            Text(
                text = stringResource(R.string.no_upcoming_trips_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun EmptyTripsState(
    modifier: Modifier = Modifier, onCreateTrip: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue)
            )
        }

        Spacer(Modifier.height(CoTripTokens.spacing.x2))
        Text(
            text = stringResource(R.string.no_trips_yet),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(CoTripTokens.spacing.x1))
        Text(
            text = stringResource(R.string.no_trips_yet_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(CoTripTokens.spacing.x2_5))
        PrimaryButton(
            text = stringResource(R.string.create_trip), onClick = onCreateTrip
        )
    }
}

private fun LazyListState.calculatePastTripsHederIndex() =
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == KEY_PAST_HEADER }?.index
