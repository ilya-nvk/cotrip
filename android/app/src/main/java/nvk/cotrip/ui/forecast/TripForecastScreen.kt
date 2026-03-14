package nvk.cotrip.ui.forecast

import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripCard
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.CoTripListItem
import nvk.cotrip.ui.components.TertiaryTextButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.TextSecondary

private const val KEY_CARD = "forecast_card"
private const val KEY_FOOTER = "footer"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TripForecastScreen(
    viewModel: TripForecastViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val citySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(TripForecastEvent.OnAutoRefresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is TripForecastEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }

    val contentState = state as? TripForecastState.Content
    if (contentState?.isCityPickerVisible == true) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(TripForecastEvent.OnDismissCityPicker) },
            sheetState = citySheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            CityPickerSheet(
                cities = contentState.cityOptions,
                onSelect = { viewModel.onEvent(TripForecastEvent.OnCitySelected(it)) }
            )
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
                        onClick = { viewModel.onEvent(TripForecastEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.weather_forecast_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {},
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        when (val uiState = state) {
            TripForecastState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is TripForecastState.Content -> {
                val pullRefreshState = rememberPullRefreshState(
                    refreshing = uiState.isRefreshing,
                    onRefresh = { viewModel.onEvent(TripForecastEvent.OnUserRefresh) },
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
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = CoTripTokens.spacing.x0_5),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TertiaryTextButton(
                                    text = uiState.city.ifBlank {
                                        stringResource(R.string.weather_forecast_city_missing)
                                    },
                                    onClick = { viewModel.onEvent(TripForecastEvent.OnCityClick) }
                                )
                                Icon(
                                    imageVector = CoTripIcons.ExpandMore,
                                    contentDescription = null,
                                    tint = TextSecondary
                                )
                            }
                        }

                        uiState.coverageMessage?.let { message ->
                            item {
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = CoTripTokens.spacing.x0_5)
                                )
                            }
                        }

                        item(key = KEY_CARD) {
                            if (uiState.days.isEmpty()) {
                                CoTripCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(CoTripTokens.spacing.x2)
                                ) {
                                    Text(
                                        text = stringResource(R.string.weather_forecast_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextSecondary
                                    )
                                }
                            } else {
                                CoTripCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    uiState.days.forEachIndexed { index, day ->
                                        ForecastRow(
                                            day = day,
                                            showDivider = index != uiState.days.lastIndex
                                        )
                                    }
                                }
                            }
                        }

                        item(key = KEY_FOOTER) {
                            Footer(
                                disclaimer = stringResource(R.string.weather_forecast_disclaimer),
                                source = stringResource(
                                    R.string.weather_forecast_source,
                                    uiState.source
                                ),
                                updated = uiState.lastUpdated.takeIf { it.isNotBlank() }
                                    ?.let { value ->
                                        stringResource(R.string.weather_forecast_updated, value)
                                    }
                            )
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
private fun ForecastRow(
    day: ForecastDayUi,
    showDivider: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(horizontal = CoTripTokens.spacing.x2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
        ) {
            Text(
                text = day.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            day.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Box(
            modifier = Modifier
                .width(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = day.icon,
                contentDescription = null,
                tint = day.iconTint
            )
        }

        Spacer(Modifier.width(CoTripTokens.spacing.x2))

        Column(
            modifier = Modifier.width(90.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
        ) {
            Text(
                text = day.temp,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = day.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.End,
            )
        }
    }

    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = CoTripTokens.spacing.x2)
                .background(Border)
        )
    }
}

@Composable
private fun CityPickerSheet(
    cities: List<String>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = CoTripTokens.spacing.x2,
                vertical = CoTripTokens.spacing.x2
            ),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2),
    ) {
        Text(
            text = stringResource(R.string.itinerary_choose_city_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentPadding = PaddingValues(vertical = CoTripTokens.spacing.x1),
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5),
        ) {
            items(cities.size, key = { index -> cities[index] }) { index ->
                val city = cities[index]
                CoTripListItem(
                    title = city,
                    onClick = { onSelect(city) },
                )
            }
        }
    }
}

@Composable
private fun ColumnText(
    title: String,
    subtitle: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun ColumnRight(
    temp: String,
    description: String
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
    ) {
        Text(
            text = temp,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun Footer(
    disclaimer: String,
    source: String,
    updated: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CoTripTokens.spacing.x1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
    ) {
        Text(
            text = disclaimer,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Text(
            text = source,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        updated?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
