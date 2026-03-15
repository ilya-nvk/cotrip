package nvk.cotrip.ui.activity.details

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.DestructiveOutlinedButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.PrimaryBlue
import nvk.cotrip.ui.theme.TextMedium
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary

private const val KEY_NOTES_MAX_LINES = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailsScreen(
    viewModel: ActivityDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val canEdit = (state as? ActivityDetailsState.Content)?.isPastTrip == false

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(ActivityDetailsEvent.OnRefresh)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is ActivityDetailsEffect.ShowToastRes ->
                    Toast.makeText(context, context.getString(effect.resId), Toast.LENGTH_SHORT)
                        .show()
                is ActivityDetailsEffect.OpenExternalLink -> {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(effect.url))
                    val opened = runCatching {
                        context.startActivity(intent)
                    }.isSuccess
                    if (!opened) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.common_error_message),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
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
                        onClick = { viewModel.onEvent(ActivityDetailsEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.activity_details_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                },
                actions = {
                    if (canEdit) {
                        CoTripIconButton(
                            icon = CoTripIcons.Edit,
                            contentDescription = null,
                            onClick = { viewModel.onEvent(ActivityDetailsEvent.OnEditClick) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            if (canEdit) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(
                            horizontal = CoTripTokens.spacing.x2,
                            vertical = CoTripTokens.spacing.x2
                        )
                ) {
                    DestructiveOutlinedButton(
                        text = stringResource(R.string.activity_details_delete),
                        onClick = { viewModel.onEvent(ActivityDetailsEvent.OnDeleteClick) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(
                                imageVector = CoTripIcons.Delete,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        when (val uiState = state) {
            is ActivityDetailsState.Init -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ActivityDetailsState.Content -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = CoTripTokens.spacing.x2),
                    verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
                ) {
                    val dayAndCity = if (uiState.city.isNullOrBlank()) {
                        stringResource(
                            R.string.activity_details_day_only,
                            uiState.dayNumber
                        )
                    } else {
                        stringResource(
                            R.string.activity_details_day_with_city,
                            uiState.dayNumber,
                            uiState.city
                        )
                    }
                    Spacer(Modifier.height(CoTripTokens.spacing.x1))

                    Text(
                        text = dayAndCity.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )

                    Text(
                        text = uiState.title,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    SectionLabel(text = stringResource(R.string.activity_details_date_time_label))
                    InfoRow(
                        icon = CoTripIcons.Schedule,
                        title = uiState.dateText,
                        subtitle = uiState.timeText,
                        trailing = null
                    )

                    Divider()

                    SectionLabel(text = stringResource(R.string.activity_details_location_label))
                    InfoRow(
                        icon = CoTripIcons.Location,
                        title = uiState.locationName
                            ?: stringResource(R.string.activity_details_empty),
                        subtitle = null,
                        trailing = null
                    )

                    Divider()

                    SectionLabel(text = stringResource(R.string.activity_details_link_label))
                    InfoRow(
                        icon = CoTripIcons.Link,
                        title = uiState.link ?: stringResource(R.string.activity_details_empty),
                        subtitle = null,
                        trailing = if (uiState.link != null) {
                            {
                                CoTripIconButton(
                                    icon = CoTripIcons.OpenInNew,
                                    contentDescription = null,
                                    onClick = { viewModel.onEvent(ActivityDetailsEvent.OnOpenLinkClick) }
                                )
                            }
                        } else null,
                        titleColor = if (uiState.link != null) PrimaryBlue else TextMedium
                    )

                    Divider()

                    SectionLabel(text = stringResource(R.string.activity_details_cost_label))
                    InfoRow(
                        icon = CoTripIcons.AccountBalance,
                        title = uiState.costText ?: stringResource(R.string.activity_details_empty),
                        subtitle = if (uiState.costText != null) stringResource(R.string.activity_details_per_person) else null,
                        trailing = null
                    )

                    Divider()

                    SectionLabel(text = stringResource(R.string.activity_details_notes_label))
                    Text(
                        text = uiState.notes ?: stringResource(R.string.activity_details_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (uiState.notes != null) TextPrimary else TextSecondary,
                        maxLines = KEY_NOTES_MAX_LINES,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(110.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary
    )
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    trailing: (@Composable () -> Unit)?,
    titleColor: androidx.compose.ui.graphics.Color = TextPrimary
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CoTripTokens.spacing.x1),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary
        )

        Spacer(Modifier.width(CoTripTokens.spacing.x2))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        }

        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Border)
    )
}
