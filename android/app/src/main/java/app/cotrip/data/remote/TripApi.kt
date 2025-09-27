package app.cotrip.data.remote

import app.cotrip.domain.model.Trip
import retrofit2.http.GET

interface TripApi {
    @GET("trips")
    suspend fun getTrips(): List<Trip>
}
