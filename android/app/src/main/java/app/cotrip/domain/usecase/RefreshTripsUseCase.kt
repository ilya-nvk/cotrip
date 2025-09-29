package app.cotrip.domain.usecase

import app.cotrip.domain.repository.TripRepository
import javax.inject.Inject

class RefreshTripsUseCase @Inject constructor(
    private val repository: TripRepository
) {
    suspend operator fun invoke() = repository.refreshTrips()
}
