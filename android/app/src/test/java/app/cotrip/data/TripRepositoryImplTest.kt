package app.cotrip.data

import app.cotrip.data.local.TripDao
import app.cotrip.data.local.TripEntity
import app.cotrip.data.remote.TripApi
import app.cotrip.domain.model.Trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private class FakeTripDao : TripDao {
    private val tripsFlow = MutableStateFlow<List<TripEntity>>(emptyList())

    override fun observeTrips(): Flow<List<TripEntity>> = tripsFlow

    override suspend fun insertTrips(trips: List<TripEntity>) {
        tripsFlow.value = trips
    }

    override suspend fun clearTrips() {
        tripsFlow.value = emptyList()
    }
}

private class FakeTripApi(private val trips: List<Trip>) : TripApi {
    override suspend fun getTrips(): List<Trip> = trips
}

@OptIn(ExperimentalCoroutinesApi::class)
class TripRepositoryImplTest {

    private lateinit var tripDao: FakeTripDao
    private lateinit var tripApi: TripApi
    private lateinit var repository: TripRepositoryImpl

    @Before
    fun setup() {
        tripDao = FakeTripDao()
        tripApi = FakeTripApi(
            trips = listOf(Trip(id = "1", destination = "Paris", startDate = "2024-05-01", endDate = "2024-05-07"))
        )
        repository = TripRepositoryImpl(tripDao, tripApi)
    }

    @Test
    fun `refreshTrips stores remote data`() = runBlocking {
        repository.refreshTrips()
        val trips = repository.observeTrips().first()
        assertEquals(1, trips.size)
        assertEquals("Paris", trips.first().destination)
    }
}
