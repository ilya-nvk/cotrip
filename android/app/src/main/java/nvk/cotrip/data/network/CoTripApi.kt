package nvk.cotrip.data.network

import nvk.cotrip.data.network.dto.AuthDevRequest
import nvk.cotrip.data.network.dto.AuthGoogleRequest
import nvk.cotrip.data.network.dto.AuthResponse
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.InviteInfoDto
import nvk.cotrip.data.network.dto.InviteLinkDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.TransferOwnerRequest
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.UpdateTripRequest
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.network.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CoTripApi {
    @POST("v1/auth/dev")
    suspend fun devAuth(@Body request: AuthDevRequest): AuthResponse

    @POST("v1/auth/google")
    suspend fun googleAuth(@Body request: AuthGoogleRequest): AuthResponse

    @GET("v1/users/me")
    suspend fun getMe(): UserDto

    @PATCH("v1/users/me")
    suspend fun updateMe(@Body request: UpdateUserRequest): UserDto

    @DELETE("v1/users/me")
    suspend fun deleteMe(): Unit

    @POST("v1/trips")
    suspend fun createTrip(@Body request: CreateTripRequest): TripDto

    @GET("v1/trips")
    suspend fun listTrips(@Query("status") status: String? = null): ApiListResponse<TripDto>

    @GET("v1/trips/{tripId}")
    suspend fun getTrip(@Path("tripId") tripId: String): TripDto

    @PATCH("v1/trips/{tripId}")
    suspend fun updateTrip(
        @Path("tripId") tripId: String,
        @Body request: UpdateTripRequest,
    ): TripDto

    @DELETE("v1/trips/{tripId}")
    suspend fun deleteTrip(@Path("tripId") tripId: String): Unit

    @POST("v1/trips/{tripId}/archive")
    suspend fun archiveTrip(@Path("tripId") tripId: String): Unit

    @POST("v1/trips/{tripId}/transfer-owner")
    suspend fun transferOwner(
        @Path("tripId") tripId: String,
        @Body request: TransferOwnerRequest,
    ): Unit

    @POST("v1/trips/{tripId}/invite")
    suspend fun createInvite(@Path("tripId") tripId: String): InviteLinkDto

    @GET("v1/invites/{token}")
    suspend fun getInvite(@Path("token") token: String): InviteInfoDto

    @POST("v1/invites/{token}/accept")
    suspend fun acceptInvite(@Path("token") token: String): Map<String, String>

    @GET("v1/trips/{tripId}/members")
    suspend fun listMembers(@Path("tripId") tripId: String): ApiListResponse<MemberDto>

    @DELETE("v1/trips/{tripId}/members/{memberId}")
    suspend fun removeMember(
        @Path("tripId") tripId: String,
        @Path("memberId") memberId: String,
    ): Unit

    @GET("v1/trips/{tripId}/ideas")
    suspend fun listIdeas(
        @Path("tripId") tripId: String,
        @Query("search") search: String? = null,
        @Query("status") status: String? = null,
        @Query("authorId") authorId: String? = null,
        @Query("city") city: String? = null,
    ): ApiListResponse<IdeaDto>

    @GET("v1/trips/{tripId}/expenses")
    suspend fun listExpenses(
        @Path("tripId") tripId: String,
    ): ApiListResponse<ExpenseDto>

    @GET("v1/trips/{tripId}/itinerary")
    suspend fun getItinerary(
        @Path("tripId") tripId: String,
    ): ApiListResponse<ItineraryDayDto>

    @POST("v1/trips/{tripId}/itinerary/trim-out-of-range")
    suspend fun trimOutOfRangeDays(
        @Path("tripId") tripId: String,
        @Body request: TrimOutOfRangeRequest,
    ): Unit
}
