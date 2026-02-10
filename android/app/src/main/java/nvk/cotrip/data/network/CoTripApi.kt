package nvk.cotrip.data.network

import nvk.cotrip.data.network.dto.AuthDevRequest
import nvk.cotrip.data.network.dto.AuthGoogleRequest
import nvk.cotrip.data.network.dto.AuthResponse
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.dto.CitySuggestionDto
import nvk.cotrip.data.network.dto.ExpenseCreateRequest
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.ExpenseUpdateRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.InviteInfoDto
import nvk.cotrip.data.network.dto.InviteLinkDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.NotificationListResponse
import nvk.cotrip.data.network.dto.NotificationSettingsResponse
import nvk.cotrip.data.network.dto.NotificationSettingsUpdateRequest
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.TransferOwnerRequest
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.MoveActivityRequest
import nvk.cotrip.data.network.dto.ReorderActivitiesRequest
import nvk.cotrip.data.network.dto.UpdateActivityRequest
import nvk.cotrip.data.network.dto.UpdateDayRequest
import nvk.cotrip.data.network.dto.UpdateIdeaRequest
import nvk.cotrip.data.network.dto.UpdateTripRequest
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.network.dto.UserDto
import nvk.cotrip.data.network.dto.PlaceSuggestionDto
import nvk.cotrip.data.network.dto.SyncChangesRequest
import nvk.cotrip.data.network.dto.SyncChangesResponse
import nvk.cotrip.data.network.dto.SyncPullResponse
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

    @POST("v1/trips/{tripId}/ideas")
    suspend fun createIdea(
        @Path("tripId") tripId: String,
        @Body request: CreateIdeaRequest,
    ): IdeaDto

    @GET("v1/ideas/{ideaId}")
    suspend fun getIdea(@Path("ideaId") ideaId: String): IdeaDto

    @PATCH("v1/ideas/{ideaId}")
    suspend fun updateIdea(
        @Path("ideaId") ideaId: String,
        @Body request: UpdateIdeaRequest,
    ): IdeaDto

    @DELETE("v1/ideas/{ideaId}")
    suspend fun deleteIdea(@Path("ideaId") ideaId: String): Unit

    @POST("v1/ideas/{ideaId}/convert-to-activity")
    suspend fun convertIdeaToActivity(
        @Path("ideaId") ideaId: String,
        @Body request: ConvertIdeaRequest,
    ): Unit

    @POST("v1/ideas/{ideaId}/approve")
    suspend fun approveIdea(@Path("ideaId") ideaId: String): IdeaDto

    @POST("v1/ideas/{ideaId}/reject")
    suspend fun rejectIdea(@Path("ideaId") ideaId: String): IdeaDto

    @GET("v1/ideas/{ideaId}/comments")
    suspend fun listComments(@Path("ideaId") ideaId: String): ApiListResponse<CommentDto>

    @DELETE("v1/comments/{commentId}")
    suspend fun deleteComment(@Path("commentId") commentId: String): Unit

    @GET("v1/trips/{tripId}/expenses")
    suspend fun listExpenses(
        @Path("tripId") tripId: String,
    ): ApiListResponse<ExpenseDto>

    @POST("v1/trips/{tripId}/expenses")
    suspend fun createExpense(
        @Path("tripId") tripId: String,
        @Body request: ExpenseCreateRequest,
    ): ExpenseDto

    @GET("v1/expenses/{expenseId}")
    suspend fun getExpense(@Path("expenseId") expenseId: String): ExpenseDto

    @PATCH("v1/expenses/{expenseId}")
    suspend fun updateExpense(
        @Path("expenseId") expenseId: String,
        @Body request: ExpenseUpdateRequest,
    ): ExpenseDto

    @DELETE("v1/expenses/{expenseId}")
    suspend fun deleteExpense(@Path("expenseId") expenseId: String): Unit

    @GET("v1/trips/{tripId}/itinerary")
    suspend fun getItinerary(
        @Path("tripId") tripId: String,
    ): ApiListResponse<ItineraryDayDto>

    @GET("v1/trips/{tripId}/cities/search")
    suspend fun searchCities(
        @Path("tripId") tripId: String,
        @Query("query") query: String,
        @Query("limit") limit: Int = 8,
    ): ApiListResponse<CitySuggestionDto>

    @GET("v1/trips/{tripId}/places/search")
    suspend fun searchPlaces(
        @Path("tripId") tripId: String,
        @Query("query") query: String,
        @Query("limit") limit: Int = 8,
    ): ApiListResponse<PlaceSuggestionDto>

    @PATCH("v1/itinerary/days/{dayId}")
    suspend fun updateDay(
        @Path("dayId") dayId: String,
        @Body request: UpdateDayRequest,
    ): Unit

    @POST("v1/itinerary/days/{dayId}/activities")
    suspend fun createActivity(
        @Path("dayId") dayId: String,
        @Body request: CreateActivityRequest,
    ): ActivityDto

    @PATCH("v1/itinerary/activities/{activityId}")
    suspend fun updateActivity(
        @Path("activityId") activityId: String,
        @Body request: UpdateActivityRequest,
    ): ActivityDto

    @DELETE("v1/itinerary/activities/{activityId}")
    suspend fun deleteActivity(@Path("activityId") activityId: String): Unit

    @POST("v1/itinerary/activities/{activityId}/move")
    suspend fun moveActivity(
        @Path("activityId") activityId: String,
        @Body request: MoveActivityRequest,
    ): ActivityDto

    @POST("v1/itinerary/days/{dayId}/activities/reorder")
    suspend fun reorderActivities(
        @Path("dayId") dayId: String,
        @Body request: ReorderActivitiesRequest,
    ): Unit

    @POST("v1/trips/{tripId}/itinerary/trim-out-of-range")
    suspend fun trimOutOfRangeDays(
        @Path("tripId") tripId: String,
        @Body request: TrimOutOfRangeRequest,
    ): Unit

    @POST("v1/sync/changes")
    suspend fun postSyncChanges(@Body request: SyncChangesRequest): SyncChangesResponse

    @GET("v1/sync/changes")
    suspend fun getSyncChanges(@Query("since") since: String): SyncPullResponse

    @GET("v1/notifications")
    suspend fun listNotifications(): NotificationListResponse

    @PATCH("v1/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): Unit

    @GET("v1/users/me/notification-settings")
    suspend fun getNotificationSettings(): NotificationSettingsResponse

    @PATCH("v1/users/me/notification-settings")
    suspend fun updateNotificationSettings(
        @Body request: NotificationSettingsUpdateRequest
    ): NotificationSettingsResponse
}
