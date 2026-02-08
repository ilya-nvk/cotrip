package nvk.cotrip.ui.trip.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import nvk.cotrip.R

@Composable
fun CreateTripScreen(
    viewModel: CreateTripViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    TripFormHost(
        titleRes = R.string.create_trip_title,
        primaryButtonRes = R.string.create_trip_primary_button,
        showAdvanced = false,
        state = state,
        effects = viewModel.effects,
        onEvent = viewModel::onEvent,
    )
}