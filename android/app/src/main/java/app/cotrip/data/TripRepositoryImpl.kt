package app.cotrip.data

import app.cotrip.data.local.TripDao
import app.cotrip.data.local.TripEntity
import app.cotrip.data.remote.TripApi
import app.cotrip.domain.model.Trip
import app.cotrip.domain.repository.TripRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripDao: TripDao,
    private val tripApi: TripApi
) : TripRepository {

    override fun observeTrips(): Flow<List<Trip>> = tripDao.observeTrips().map { entities ->
        entities.map { it.toDomain() }
    }

    override suspend fun refreshTrips() {
        val remoteTrips = try {
            tripApi.getTrips()
        } catch (e: Exception) {
            emptyList()
        }
        if (remoteTrips.isNotEmpty()) {
            tripDao.clearTrips()
            tripDao.insertTrips(remoteTrips.map { it.toEntity() })
        }
    }

    private fun TripEntity.toDomain(): Trip = Trip(
        id = id,
        destination = destination,
        startDate = startDate,
        endDate = endDate
    )

    private fun Trip.toEntity(): TripEntity = TripEntity(
        id = id,
        destination = destination,
        startDate = startDate,
        endDate = endDate
    )
}
