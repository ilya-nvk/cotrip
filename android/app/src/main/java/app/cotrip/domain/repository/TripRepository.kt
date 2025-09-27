package app.cotrip.domain.repository

import app.cotrip.domain.model.Trip
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun observeTrips(): Flow<List<Trip>>
    suspend fun refreshTrips()
}
