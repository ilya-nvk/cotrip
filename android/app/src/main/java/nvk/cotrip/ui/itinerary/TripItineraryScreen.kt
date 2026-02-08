package nvk.cotrip.ui.itinerary

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripCard
import nvk.cotrip.ui.components.CoTripFab
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.TertiaryTextButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary

private const val KEY_HEADER = "header"
private const val KEY_BOTTOM_SPACER = "bottom_spacer"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripItineraryScreen(
    viewModel: TripItineraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                cities = currentState.filteredCities,
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
            item(key = KEY_HEADER) {
                Spacer(Modifier.height(0.dp))
            }

            when (state.mode) {
                ItineraryMode.Filled -> {
                    items(state.days, key = { it.id }) { day ->
                        FilledDaySection(
                            day = day,
                            onChooseCity = {
                                viewModel.onEvent(
                                    TripItineraryEvent.OnChooseCityClick(day.id)
                                )
                            },
                            onActivityClick = {
                                viewModel.onEvent(
                                    TripItineraryEvent.OnActivityClick(it)
                                )
                            }
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
    }
}

@Composable
private fun FilledDaySection(
    day: ItineraryDayUi,
    onChooseCity: () -> Unit,
    onActivityClick: (String) -> Unit
) {
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
                ActivityRow(
                    activity = activity,
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
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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
    cities: List<String>,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
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

        nvk.cotrip.ui.components.CoTripTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.itinerary_choose_city_placeholder),
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentPadding = PaddingValues(vertical = CoTripTokens.spacing.x1),
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
        ) {
            items(cities, key = { it }) { city ->
                nvk.cotrip.ui.components.CoTripListItem(
                    title = city,
                    onClick = { onSelect(city) }
                )
            }
        }
    }
}

@Composable
private fun BorderStrokeCompat() =
    androidx.compose.foundation.BorderStroke(1.dp, Border)