package nvk.cotrip.ui.aisuggestions

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nvk.cotrip.R
import nvk.cotrip.ui.components.CoTripIconButton
import nvk.cotrip.ui.components.CoTripListItem
import nvk.cotrip.ui.components.CoTripTextField
import nvk.cotrip.ui.components.PrimaryButton
import nvk.cotrip.ui.theme.Border
import nvk.cotrip.ui.theme.CoTripIcons
import nvk.cotrip.ui.theme.CoTripTokens
import nvk.cotrip.ui.theme.PrimaryBlue
import nvk.cotrip.ui.theme.TextPrimary
import nvk.cotrip.ui.theme.TextSecondary
import nvk.cotrip.ui.theme.WhiteCards

private val HintPurple = Color(0xFFEEDCF7)
private val HintPurpleBorder = Color(0xFFD5B8E6)
private val HintPurpleText = Color(0xFF6A1B9A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildRouteScreen(
    viewModel: BuildRouteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val cityPicker = state.cityPicker
    if (cityPicker != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(BuildRouteEvent.OnDismissCityPicker) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CityPickerSheet(
                cities = cityPicker.cities,
                onSelect = { viewModel.onEvent(BuildRouteEvent.OnCitySelected(it)) }
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
                        onClick = { viewModel.onEvent(BuildRouteEvent.OnBackClick) }
                    )
                },
                title = {
                    Text(
                        text = stringResource(R.string.ai_suggestions_title),
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
        bottomBar = {
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
                PrimaryButton(
                    text = stringResource(R.string.ai_suggestions_generate),
                    onClick = { viewModel.onEvent(BuildRouteEvent.OnGenerateClick) },
                    enabled = state.city != null,
                    modifier = Modifier.fillMaxWidth()
                )
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
            item {
                HintCard()
            }

            item {
                FieldLabel(stringResource(R.string.ai_suggestions_city_label))
                CitySelector(
                    city = state.city,
                    onClick = { viewModel.onEvent(BuildRouteEvent.OnCityClick) }
                )
                FieldHint(stringResource(R.string.ai_suggestions_city_hint))
            }

            item {
                FieldLabel(stringResource(R.string.ai_suggestions_description_label))
                CoTripTextField(
                    value = state.description,
                    onValueChange = { viewModel.onEvent(BuildRouteEvent.OnDescriptionChange(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    placeholder = stringResource(R.string.ai_suggestions_description_placeholder),
                    singleLine = false,
                    maxLines = 4
                )
                FieldHint(stringResource(R.string.ai_suggestions_description_hint))
            }

            item {
                Text(
                    text = stringResource(R.string.ai_suggestions_quick_preferences),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }

            item {
                OptionSection(
                    title = stringResource(R.string.ai_suggestions_type_label),
                    options = state.typeOptions,
                    onClick = { viewModel.onEvent(BuildRouteEvent.OnTypeToggle(it)) }
                )
            }

            item {
                OptionSection(
                    title = stringResource(R.string.ai_suggestions_time_of_day_label),
                    options = state.timeOfDayOptions,
                    onClick = { viewModel.onEvent(BuildRouteEvent.OnTimeOfDaySelect(it)) }
                )
            }

            item {
                OptionSection(
                    title = stringResource(R.string.ai_suggestions_budget_label),
                    options = state.budgetOptions,
                    onClick = { viewModel.onEvent(BuildRouteEvent.OnBudgetSelect(it)) }
                )
            }

            item {
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun HintCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, HintPurpleBorder),
        color = HintPurple
    ) {
        Row(
            modifier = Modifier.padding(CoTripTokens.spacing.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
        ) {
            Text(
                text = stringResource(R.string.ai_suggestions_hint_symbol),
                style = MaterialTheme.typography.headlineSmall,
                color = HintPurpleText
            )
            Text(
                text = stringResource(R.string.ai_suggestions_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = HintPurpleText
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = TextPrimary,
        modifier = Modifier.padding(bottom = CoTripTokens.spacing.x1)
    )
}

@Composable
private fun FieldHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = TextSecondary,
        modifier = Modifier.padding(top = CoTripTokens.spacing.x1)
    )
}

@Composable
private fun CitySelector(
    city: String?,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, Border),
        color = WhiteCards
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = CoTripTokens.spacing.x2,
                    vertical = CoTripTokens.spacing.x1_5
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)
        ) {
            if (city != null) {
                Icon(
                    imageVector = CoTripIcons.Location,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = city ?: stringResource(R.string.ai_suggestions_select_city),
                style = MaterialTheme.typography.bodyLarge,
                color = if (city == null) TextSecondary else TextPrimary,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = CoTripIcons.ExpandMore,
                contentDescription = null,
                tint = TextSecondary
            )
        }
    }
}

@Composable
private fun OptionSection(
    title: String,
    options: List<AiOptionUi>,
    onClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = TextSecondary
        )

        options.chunked(4).forEach { rowOptions ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1),
                modifier = Modifier.padding(bottom = CoTripTokens.spacing.x1)
            ) {
                rowOptions.forEach { option ->
                    PreferenceChip(
                        text = option.label,
                        selected = option.selected,
                        onClick = { onClick(option.label) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) PrimaryBlue else Border
    val containerColor = if (selected) PrimaryBlue.copy(alpha = 0.12f) else Color.Transparent
    val contentColor = if (selected) PrimaryBlue else TextPrimary

    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
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
        verticalArrangement = Arrangement.spacedBy(CoTripTokens.spacing.x1_5)
    ) {
        Text(
            text = stringResource(R.string.ai_suggestions_select_city_title),
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )

        cities.forEach { city ->
            CoTripListItem(
                title = city,
                onClick = { onSelect(city) }
            )
        }
    }
}
