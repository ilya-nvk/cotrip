package nvk.cotrip.ui.trip.details

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.AvatarStackItem
import nvk.cotrip.ui.components.AvatarsStack
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

private const val KEY_TRAVELERS = "travelers"
private const val KEY_WEATHER = "weather"
private const val KEY_NEXT = "next"
private const val KEY_START_PLANNING = "start_planning"
private const val KEY_OVERVIEW = "overview"
private const val KEY_CTA = "cta"
private const val KEY_SPACER = "spacer"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TripDetailsScreen(
    viewModel: TripDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(TripDetailsEvent.OnAutoRefresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is TripDetailsEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
            }
        }
    }

    val density = LocalDensity.current
    val statusBarTopInset = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    if (state is TripDetailsState.Loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }
    val content = state as TripDetailsState.Content

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        val pullRefreshState = rememberPullRefreshState(
            refreshing = content.isRefreshing,
            onRefresh = { viewModel.onEvent(TripDetailsEvent.OnUserRefresh) }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = CoTripTokens.spacing.x2),
                verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
            ) {
                item {
                    Header(
                        tripId = content.header.tripId,
                        title = content.header.title,
                        dateRange = content.header.dateRange,
                        locationLine = content.header.locationLine,
                        coverUrl = content.header.coverUrl,
                        statusBarTopInset = statusBarTopInset,
                        canEdit = content.isOwner && !content.isPast,
                        onBack = { viewModel.onEvent(TripDetailsEvent.OnBackClick) },
                        onEdit = { viewModel.onEvent(TripDetailsEvent.OnEditClick) }
                    )
                }

                item(key = KEY_TRAVELERS) {
                    TravelersSection(
                        title = stringResource(R.string.trip_details_travelers),
                        travelers = content.travelers,
                        peopleCountText = content.peopleCountText,
                        canInvite = content.isOwner && !content.isPast,
                        onInvite = { viewModel.onEvent(TripDetailsEvent.OnInviteTravelersClick) },
                        onMembers = { viewModel.onEvent(TripDetailsEvent.OnMembersClick) }
                    )
                }

                if (!content.isPast && content.weather.days.isNotEmpty()) {
                    item(key = KEY_WEATHER) {
                        WeatherCard(
                            city = content.weather.city,
                            days = content.weather.days,
                            notice = content.weather.notice,
                            onViewForecast = { viewModel.onEvent(TripDetailsEvent.OnViewForecastClick) }
                        )
                    }
                }

                item(key = KEY_NEXT) {
                    NextInTripCard(
                        title = stringResource(R.string.trip_details_next_in_trip),
                        subtitle = content.nextInTrip.subtitle,
                        lines = content.nextInTrip.lines,
                        onViewItinerary = { viewModel.onEvent(TripDetailsEvent.OnViewItineraryClick) }
                    )
                }

                if (!content.isPast && content.isEmpty) {
                    item(key = KEY_START_PLANNING) {
                        StartPlanningCard(
                            title = stringResource(R.string.trip_details_start_planning),
                            text = stringResource(R.string.trip_details_start_planning_text),
                            actionText = stringResource(R.string.trip_details_browse_ideas),
                            onClick = { viewModel.onEvent(TripDetailsEvent.OnBrowseIdeasClick) }
                        )
                    }
                }

                item(key = KEY_OVERVIEW) {
                    OverviewSection(
                        title = stringResource(R.string.trip_details_overview),
                        isPastTrip = content.isPast,
                        showIdeas = !content.isPast,
                        ideasCount = content.overview.ideasCount,
                        ideasSubtitle = content.overview.ideasSubtitle,
                        expensesAmount = content.overview.expensesAmount,
                        expensesSubtitle = content.overview.expensesSubtitle,
                        onIdeasClick = { viewModel.onEvent(TripDetailsEvent.OnIdeasClick) },
                        onExpensesClick = { viewModel.onEvent(TripDetailsEvent.OnExpensesClick) }
                    )
                }

                if (!content.isPast) {
                    item(key = KEY_CTA) {
                        PrimaryButton(
                            text = if (content.isEmpty)
                                stringResource(R.string.trip_details_build_route)
                            else
                                stringResource(R.string.trip_details_get_route_suggestions),
                            onClick = { viewModel.onEvent(TripDetailsEvent.OnPrimaryCtaClick) },
                            enabled = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CoTripTokens.spacing.x2)
                        )
                    }
                }

                item(key = KEY_SPACER) {
                    Spacer(Modifier.height(80.dp))
                }
            }
            PullRefreshIndicator(
                refreshing = content.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun Header(
    tripId: String,
    title: String,
    dateRange: String,
    locationLine: String,
    coverUrl: String?,
    statusBarTopInset: Dp,
    canEdit: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(
                RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 0.dp,
                    bottomStart = CoTripTokens.radius.large,
                    bottomEnd = CoTripTokens.radius.large
                )
            )
            .background(tripGradientFromId(tripId))
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.28f),
                            0.35f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.42f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CoTripTokens.spacing.x2,
                    top = statusBarTopInset + CoTripTokens.spacing.x1_5,
                    end = CoTripTokens.spacing.x2,
                    bottom = CoTripTokens.spacing.x2
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = CoTripTokens.elevation.cardHover
            ) {
                CoTripIconButton(
                    icon = CoTripIcons.ArrowBack,
                    contentDescription = stringResource(R.string.trip_details_back),
                    onClick = onBack
                )
            }

            if (canEdit) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = CoTripTokens.elevation.cardHover
                ) {
                    CoTripIconButton(
                        icon = CoTripIcons.Edit,
                        contentDescription = stringResource(R.string.trip_details_edit),
                        onClick = onEdit
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = CoTripTokens.spacing.x2,
                    end = CoTripTokens.spacing.x2,
                    bottom = CoTripTokens.spacing.x1_5
                ),
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = dateRange,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text(
                text = locationLine,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun TravelersSection(
    title: String,
    travelers: List<AvatarStackItem>,
    peopleCountText: String,
    canInvite: Boolean,
    onInvite: () -> Unit,
    onMembers: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoTripTokens.spacing.x2),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (travelers.isNotEmpty()) {
                AvatarsStack(avatars = travelers.take(4), size = 34.dp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(PrimaryLight)
                        )
                    }
                }
            }

            Spacer(Modifier.width(CoTripTokens.spacing.x1_5))

            if (canInvite) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, Border)
                ) {
                    CoTripIconButton(
                        icon = CoTripIcons.Add,
                        contentDescription = stringResource(R.string.trip_details_invite),
                        onClick = onInvite
                    )
                }

                Spacer(Modifier.width(CoTripTokens.spacing.x1_5))
            }

            Text(
                text = peopleCountText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(Modifier.weight(1f))

            CoTripIconButton(
                icon = CoTripIcons.ChevronRight,
                contentDescription = stringResource(R.string.trip_details_members),
                onClick = onMembers
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Border,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Spacer(Modifier.height(1.dp))
        }
    }
}

@Composable
private fun WeatherCard(
    city: String,
    days: List<WeatherDayUi>,
    notice: WeatherCardNotice,
    onViewForecast: () -> Unit,
) {
    val cityLabel = city
        .substringBefore(',')
        .trim()
        .ifBlank { stringResource(R.string.weather_forecast_city_missing) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoTripTokens.spacing.x2),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.trip_details_weather),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CoTripTokens.radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = CoTripTokens.elevation.cardHover,
            onClick = onViewForecast
        ) {
            Column(
                modifier = Modifier.padding(CoTripTokens.spacing.x2),
                verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
            ) {
                Text(
                    text = cityLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                val noticeText = when (notice) {
                    WeatherCardNotice.None -> null
                    WeatherCardNotice.CityMissing -> stringResource(R.string.trip_details_weather_city_missing)
                    WeatherCardNotice.NoData -> stringResource(R.string.trip_details_weather_no_data)
                    WeatherCardNotice.Partial -> null
                    WeatherCardNotice.Unavailable -> stringResource(R.string.trip_details_weather_unavailable)
                }

                if (noticeText != null) {
                    Text(
                        text = noticeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                if (days.isNotEmpty()) {
                    val daysToDisplay = days.take(5)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (daysToDisplay.size < 3) {
                            Arrangement.spacedBy(CoTripTokens.spacing.x2, Alignment.Start)
                        } else {
                            Arrangement.SpaceBetween
                        }
                    ) {
                        daysToDisplay.forEach { day ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = day.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Spacer(Modifier.height(CoTripTokens.spacing.x0_5))
                                Icon(
                                    imageVector = day.icon,
                                    contentDescription = null,
                                    tint = day.tint
                                )
                                Spacer(Modifier.height(CoTripTokens.spacing.x0_5))
                                Text(
                                    text = day.temp,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.trip_details_view_full_forecast),
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryBlue
                )
            }
        }
    }
}

@Composable
private fun NextInTripCard(
    title: String,
    subtitle: String,
    lines: List<NextInTripLineUi>,
    onViewItinerary: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoTripTokens.spacing.x2),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CoTripTokens.radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = CoTripTokens.elevation.cardHover,
            onClick = onViewItinerary
        ) {
            Column(
                modifier = Modifier.padding(CoTripTokens.spacing.x2),
                verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
            ) {
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }

                if (lines.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)) {
                        lines.take(3).forEach { line ->
                            Text(
                                text = buildAnnotatedString {
                                    append("• ")
                                    if (!line.time.isNullOrBlank()) {
                                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                            append(line.time)
                                        }
                                        append(" ")
                                    }
                                    append(line.title)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.trip_details_view_full_itinerary),
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryBlue
                )
            }
        }
    }
}

@Composable
private fun StartPlanningCard(
    title: String,
    text: String,
    actionText: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoTripTokens.spacing.x2),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CoTripTokens.radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = CoTripTokens.elevation.cardHover,
            onClick = onClick
        ) {
            Column(
                modifier = Modifier.padding(CoTripTokens.spacing.x2),
                verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextMedium
                )

                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryBlue
                )
            }
        }
    }
}

@Composable
private fun OverviewSection(
    title: String,
    isPastTrip: Boolean,
    showIdeas: Boolean,
    ideasCount: Int,
    ideasSubtitle: String,
    expensesAmount: String,
    expensesSubtitle: String,
    onIdeasClick: () -> Unit,
    onExpensesClick: () -> Unit,
) {
    val isIdeasEmpty = ideasCount == 0
    val isExpensesEmpty = !isPastTrip && expensesSubtitle.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoTripTokens.spacing.x2),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )

        if (showIdeas) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CoTripTokens.radius.large),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = CoTripTokens.elevation.cardHover,
                onClick = onIdeasClick
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(CoTripTokens.spacing.x2),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(
                            if (isIdeasEmpty) CoTripTokens.spacing.x1 else CoTripTokens.spacing.x0_5
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.trip_details_ideas),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        if (isIdeasEmpty) {
                            Text(
                                text = ideasSubtitle.ifBlank {
                                    stringResource(R.string.trip_details_ideas_subtitle_empty)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = PrimaryBlue
                            )
                        } else {
                            Text(
                                text = ideasCount.toString(),
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (ideasSubtitle.isNotBlank()) {
                                Text(
                                    text = ideasSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                    if (!isIdeasEmpty) {
                        Icon(
                            imageVector = CoTripIcons.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CoTripTokens.radius.large),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = CoTripTokens.elevation.cardHover,
            onClick = onExpensesClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CoTripTokens.spacing.x2),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(
                        if (isExpensesEmpty) CoTripTokens.spacing.x1 else CoTripTokens.spacing.x0_5
                    )
                ) {
                    Text(
                        text = stringResource(R.string.trip_details_total_expenses),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    if (isExpensesEmpty) {
                        Text(
                            text = expensesSubtitle.ifBlank {
                                stringResource(R.string.trip_details_expenses_subtitle_empty)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = PrimaryBlue
                        )
                    } else {
                        Text(
                            text = expensesAmount,
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!isPastTrip && expensesSubtitle.isNotBlank()) {
                            Text(
                                text = expensesSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                if (!isExpensesEmpty) {
                    Icon(
                        imageVector = CoTripIcons.ChevronRight,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }
            }
        }
    }
}
