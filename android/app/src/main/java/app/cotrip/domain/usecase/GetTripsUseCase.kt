package app.cotrip.domain.usecase

import app.cotrip.domain.model.Trip
import app.cotrip.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTripsUseCase @Inject constructor(
    private val repository: TripRepository
) {
    operator fun invoke(): Flow<List<Trip>> = repository.observeTrips()
}
