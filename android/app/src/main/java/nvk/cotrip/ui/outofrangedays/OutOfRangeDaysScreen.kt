package nvk.cotrip.ui.outofrangedays

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.DestructiveOutlinedButton
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.TextMedium
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.Warning
import nvk.cotrip.ui.theme.WarningText

private const val KEY_WARNING = "warning"
private const val KEY_CTA = "cta"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutOfRangeDaysScreen(
    viewModel: OutOfRangeDaysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is OutOfRangeDaysEffect.ShowToastRes ->
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
                        onClick = { viewModel.onEvent(OutOfRangeDaysEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.out_of_range_days_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors().copy(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(
                        horizontal = CoTripTokens.spacing.x2,
                        vertical = CoTripTokens.spacing.x2
                    )
            ) {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x2)
                ) {
                    PrimaryButton(
                        text = stringResource(R.string.out_of_range_days_keep),
                        onClick = { viewModel.onEvent(OutOfRangeDaysEvent.OnKeepClick) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DestructiveOutlinedButton(
                        text = stringResource(
                            R.string.out_of_range_days_remove,
                            state.days.size
                        ),
                        onClick = { viewModel.onEvent(OutOfRangeDaysEvent.OnRemoveClick) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = CoTripTokens.spacing.x2),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key = KEY_WARNING) {
                WarningBanner(
                    text = stringResource(
                        R.string.out_of_range_days_banner,
                        state.dateRangeText
                    )
                )
            }

            items(state.days, key = { it.id }) { day ->
                DayItem(day = day)
                Divider()
            }

            item(key = KEY_CTA) {
                Spacer(Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun WarningBanner(
    text: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Warning)
            .padding(
                horizontal = CoTripTokens.spacing.x2,
                vertical = CoTripTokens.spacing.x2_5
            )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = WarningText
        )
    }
}

@Composable
private fun DayItem(day: OutOfRangeDayUi) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = CoTripTokens.spacing.x2,
                vertical = CoTripTokens.spacing.x2
            ),
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
    ) {
        Text(
            text = day.dayTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = day.dateText,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        Text(
            text = day.city ?: stringResource(R.string.out_of_range_days_no_city),
            style = MaterialTheme.typography.bodyLarge,
            color = if (day.city == null) TextSecondary else TextMedium,
            fontStyle = if (day.city == null) FontStyle.Italic else FontStyle.Normal
        )

        Text(
            text = day.activitiesTitle,
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )

        if (day.activitiesPreview.isNotEmpty()) {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x0_5),
                modifier = Modifier.padding(top = CoTripTokens.spacing.x0_5)
            ) {
                day.activitiesPreview.forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(nvk.cotrip.ui.theme.Border)
            .padding(horizontal = CoTripTokens.spacing.x2)
    )
}
