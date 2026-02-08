package nvk.cotrip.ui.trip.form

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import nvk.cotrip.R

@Composable
fun EditTripScreen(
    viewModel: EditTripViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    TripFormHost(
        titleRes = R.string.edit_trip_title,
        primaryButtonRes = R.string.edit_trip_primary_button,
        showAdvanced = true,
        state = state,
        effects = viewModel.effects,
        onEvent = viewModel::onEvent,
    )
}