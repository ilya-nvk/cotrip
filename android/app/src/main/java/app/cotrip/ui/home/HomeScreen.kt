package app.cotrip.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.cotrip.R
import app.cotrip.domain.model.Trip
import app.cotrip.ui.theme.CoTripTheme

@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    CoTripTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.home_title),
                    style = MaterialTheme.typography.headlineLarge
                )

                Button(onClick = { context.startActivity(viewModel.getGoogleSignInIntent()) }) {
                    Text(text = "Sign in with Google")
                }

                Button(onClick = viewModel::refreshTrips) {
                    Text(text = "Refresh Trips")
                }

                when {
                    uiState.isLoading -> CircularProgressIndicator()
                    uiState.error != null -> Text(text = uiState.error ?: "Unknown error")
                    else -> TripList(trips = uiState.trips)
                }
            }
        }
    }
}

@Composable
private fun TripList(trips: List<Trip>) {
    if (trips.isEmpty()) {
        Text(text = "No trips available yet.")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(trips) { trip ->
            TripItem(trip = trip)
        }
    }
}

@Composable
private fun TripItem(trip: Trip) {
    Surface(tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = trip.destination, style = MaterialTheme.typography.titleMedium)
            Text(text = "${trip.startDate} - ${trip.endDate}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
