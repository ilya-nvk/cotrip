package app.cotrip.ui.home

import app.cotrip.domain.model.Trip

data class HomeUiState(
    val isLoading: Boolean = true,
    val trips: List<Trip> = emptyList(),
    val error: String? = null
)
