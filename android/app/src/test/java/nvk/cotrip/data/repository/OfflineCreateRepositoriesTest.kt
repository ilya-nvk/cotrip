package nvk.cotrip.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.auth.SessionCleaner
import nvk.cotrip.data.cache.ExpensesCacheStore
import nvk.cotrip.data.cache.IdeaCommentsCacheStore
import nvk.cotrip.data.cache.IdeasCacheStore
import nvk.cotrip.data.cache.ItineraryCacheStore
import nvk.cotrip.data.cache.NotificationsCacheStore
import nvk.cotrip.data.cache.TripMembersCacheStore
import nvk.cotrip.data.cache.TripsCacheStore
import nvk.cotrip.data.cache.UserCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.ConvertIdeaRequest
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.dto.ExpenseCreateRequest
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.ExpenseParticipantDto
import nvk.cotrip.data.network.dto.ExpenseParticipantInput
import nvk.cotrip.data.network.dto.ExpenseUpdateRequest
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.NotificationDto
import nvk.cotrip.data.network.dto.NotificationSettingDto
import nvk.cotrip.data.network.dto.TrimOutOfRangeRequest
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UpdateTripRequest
import nvk.cotrip.data.network.dto.UpdateUserRequest
import nvk.cotrip.data.network.dto.UserDto
import nvk.cotrip.data.sync.CoTripDatabase
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import nvk.cotrip.data.sync.SyncScheduler
import nvk.cotrip.notifications.PushTokenSyncManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OfflineCreateRepositoriesTest {
    private lateinit var context: Context
    private lateinit var database: CoTripDatabase
    private lateinit var queue: SyncQueueRepository
    private lateinit var networkStateProvider: NetworkStateProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, CoTripDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        queue = SyncQueueRepository(
            database = database,
            scheduler = NoOpSyncScheduler(context),
            json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
        )
        networkStateProvider = NetworkStateProvider(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun given_apiOffline_when_createTrip_then_createsLocalTripAndQueueItem() = runTest {
        // GIVEN
        val api = mockk<CoTripApi>()
        coEvery { api.createTrip(any()) } throws IOException("offline")
        val tripsStore = FakeTripsCacheStore()
        val itineraryStore = FakeItineraryCacheStore()
        val userStore = FakeUserCacheStore().apply {
            setUser(UserDto(id = "user-1", name = "User One", initials = "UO"))
        }

        val repository = TripRepositoryImpl(
            api = api,
            tripsCacheStore = tripsStore,
            tripMembersCacheStore = FakeTripMembersCacheStore(),
            itineraryCacheStore = itineraryStore,
            userCacheStore = userStore,
            syncQueueRepository = queue,
            networkStateProvider = networkStateProvider,
        )

        // WHEN
        val tripId = repository.createTrip(
            CreateTripRequest(
                title = "Offline Trip",
                description = "desc",
                startDate = "2026-06-10",
                endDate = "2026-06-12",
                locationLine = "Berlin",
                coverUrl = null,
                currencyCode = "EUR",
            )
        )

        // THEN
        assertTrue(tripsStore.getTrips().any { it.id == tripId })
        assertEquals(3, itineraryStore.getItinerary(tripId).size)
        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.TRIP, pending.first().entity)
        assertEquals("create", pending.first().type)
        assertEquals(tripId, pending.first().entityId)
    }

    @Test
    fun given_apiOffline_when_createIdea_then_createsLocalIdeaAndQueueItem() = runTest {
        // GIVEN
        val api = mockk<CoTripApi>()
        coEvery { api.createIdea(any(), any()) } throws IOException("offline")
        val ideasStore = FakeIdeasCacheStore()
        val userStore = FakeUserCacheStore().apply {
            setUser(UserDto(id = "user-2", name = "User Two", initials = "UT"))
        }
        val repository = IdeaRepositoryImpl(
            api = api,
            syncQueueRepository = queue,
            ideasCacheStore = ideasStore,
            commentsCacheStore = FakeIdeaCommentsCacheStore(),
            itineraryCacheStore = FakeItineraryCacheStore(),
            userCacheStore = userStore,
            networkStateProvider = networkStateProvider,
        )

        // WHEN
        val idea = repository.createIdea(
            tripId = "trip-idea-1",
            request = CreateIdeaRequest(
                title = "Local idea",
                city = "Paris",
                link = null,
                costAmount = 25.0,
                costType = "per_person",
                notes = "note",
            )
        )

        // THEN
        assertTrue(ideasStore.getIdeas("trip-idea-1").any { it.id == idea.id })
        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.IDEA, pending.first().entity)
        assertEquals("create", pending.first().type)
        assertEquals(idea.id, pending.first().entityId)
    }

    @Test
    fun given_apiOffline_when_createExpense_then_createsLocalExpenseAndQueueItem() = runTest {
        // GIVEN
        val api = mockk<CoTripApi>()
        coEvery { api.createExpense(any(), any()) } throws IOException("offline")
        val expensesStore = FakeExpensesCacheStore()
        val repository = ExpenseRepositoryImpl(
            api = api,
            syncQueueRepository = queue,
            expensesCacheStore = expensesStore,
            networkStateProvider = networkStateProvider,
        )

        // WHEN
        val expense = repository.createExpense(
            tripId = "trip-expense-1",
            request = ExpenseCreateRequest(
                title = "Taxi",
                amount = 42.0,
                currencyCode = "EUR",
                status = "paid",
                paidById = "user-1",
                date = "2026-06-10",
                splitType = "equally",
                note = "airport",
                participants = listOf(
                    ExpenseParticipantInput(
                        userId = "user-1",
                        isIncluded = true
                    )
                ),
            )
        )

        // THEN
        assertTrue(expensesStore.getExpenses("trip-expense-1").any { it.id == expense.id })
        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.EXPENSE, pending.first().entity)
        assertEquals("create", pending.first().type)
        assertEquals(expense.id, pending.first().entityId)
    }

    @Test
    fun given_apiOffline_when_createActivity_then_createsLocalActivityAndQueueItem() = runTest {
        // GIVEN
        val api = mockk<CoTripApi>()
        coEvery { api.createActivity(any(), any()) } throws IOException("offline")
        val itineraryStore = FakeItineraryCacheStore()
        val tripId = "trip-itin-1"
        val dayId = "day-1"
        itineraryStore.setItinerary(
            tripId,
            listOf(
                ItineraryDayDto(
                    id = dayId,
                    tripId = tripId,
                    date = "2026-06-10",
                    dayNumber = 1,
                    city = null,
                    cityProviderId = null,
                    cityLat = null,
                    cityLon = null,
                    isOutOfRange = false,
                    activities = listOf(
                        ActivityDto(
                            id = "existing-activity",
                            dayId = dayId,
                            title = "Existing",
                            orderIndex = 0,
                        )
                    ),
                )
            )
        )

        val repository = ItineraryRepositoryImpl(
            api = api,
            syncQueueRepository = queue,
            itineraryCacheStore = itineraryStore,
            networkStateProvider = networkStateProvider,
        )

        // WHEN
        val created = repository.createActivity(
            dayId = dayId,
            request = CreateActivityRequest(
                title = "Offline activity",
                orderIndex = null,
            )
        )

        // THEN
        val storedDay = itineraryStore.getItinerary(tripId).first { it.id == dayId }
        assertTrue(storedDay.activities.any { it.id == created.id })
        assertEquals(1, created.orderIndex)

        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.ACTIVITY, pending.first().entity)
        assertEquals("create", pending.first().type)
        assertEquals(created.id, pending.first().entityId)
    }

    @Test
    fun given_apiOffline_when_updateTrip_then_updatesLocalTripAndQueuesUpsertAndReturnsSuccess() = runTest {
        // GIVEN
        val api = mockk<CoTripApi>()
        coEvery { api.updateTrip(any(), any()) } throws IOException("offline")
        val tripsStore = FakeTripsCacheStore().apply {
            upsertTrip(
                TripDto(
                    id = "trip-1",
                    ownerId = "owner-1",
                    title = "Before",
                    description = "desc",
                    startDate = "2026-06-10",
                    endDate = "2026-06-12",
                    locationLine = "Rome",
                    coverUrl = null,
                    currencyCode = "EUR",
                    status = "active",
                    updatedAt = "2026-01-01T00:00:00Z",
                )
            )
        }
        val repository = TripRepositoryImpl(
            api = api,
            tripsCacheStore = tripsStore,
            tripMembersCacheStore = FakeTripMembersCacheStore(),
            itineraryCacheStore = FakeItineraryCacheStore(),
            userCacheStore = FakeUserCacheStore(),
            syncQueueRepository = queue,
            networkStateProvider = networkStateProvider,
        )

        // WHEN
        val result = repository.updateTrip(
            tripId = "trip-1",
            request = UpdateTripRequest(
                title = "After",
            )
        )

        // THEN
        assertTrue(result.isSuccess)
        assertEquals("After", tripsStore.getTrips().first { it.id == "trip-1" }.title)
        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.TRIP, pending.first().entity)
        assertEquals("upsert", pending.first().type)
        assertEquals("trip-1", pending.first().entityId)
    }

    @Test
    fun given_apiOffline_when_removeMember_then_updatesLocalMembersAndQueuesDeleteCommand() = runTest {
        // GIVEN
        val tripId = "trip-members-1"
        val api = mockk<CoTripApi>()
        coEvery { api.removeMember(any(), any()) } throws IOException("offline")
        val membersStore = FakeTripMembersCacheStore().apply {
            setMembers(
                tripId = tripId,
                members = listOf(
                    MemberDto(
                        userId = "member-1",
                        name = "Member 1",
                        initials = "M1",
                        role = "owner",
                        status = "joined",
                    ),
                    MemberDto(
                        userId = "member-2",
                        name = "Member 2",
                        initials = "M2",
                        role = "member",
                        status = "joined",
                    ),
                )
            )
        }
        val repository = TripRepositoryImpl(
            api = api,
            tripsCacheStore = FakeTripsCacheStore(),
            tripMembersCacheStore = membersStore,
            itineraryCacheStore = FakeItineraryCacheStore(),
            userCacheStore = FakeUserCacheStore(),
            syncQueueRepository = queue,
            networkStateProvider = networkStateProvider,
        )

        // WHEN
        repository.removeMember(tripId = tripId, memberId = "member-2")

        // THEN
        assertEquals(1, membersStore.getMembers(tripId).size)
        assertEquals("member-1", membersStore.getMembers(tripId).first().userId)
        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.TRIP_MEMBER, pending.first().entity)
        assertEquals("delete", pending.first().type)
        assertEquals("$tripId:member-2", pending.first().entityId)
    }

    @Test
    fun given_apiOffline_when_approveIdea_then_updatesLocalStatusAndQueuesStatusCommand() = runTest {
        // GIVEN
        val api = mockk<CoTripApi>()
        coEvery { api.approveIdea(any()) } throws IOException("offline")
        val ideasStore = FakeIdeasCacheStore().apply {
            setIdeas(
                tripId = "trip-approve",
                ideas = listOf(
                    IdeaDto(
                        id = "idea-approve-1",
                        tripId = "trip-approve",
                        authorId = "user-1",
                        title = "Idea",
                        status = "pending",
                        updatedAt = "2026-01-01T00:00:00Z",
                        commentsCount = 0,
                    )
                )
            )
        }
        val repository = IdeaRepositoryImpl(
            api = api,
            syncQueueRepository = queue,
            ideasCacheStore = ideasStore,
            commentsCacheStore = FakeIdeaCommentsCacheStore(),
            itineraryCacheStore = FakeItineraryCacheStore(),
            userCacheStore = FakeUserCacheStore(),
            networkStateProvider = networkStateProvider,
        )

        // WHEN
        val approved = repository.approveIdea("idea-approve-1")

        // THEN
        assertEquals("approved", approved.status)
        assertEquals(
            "approved",
            ideasStore.findIdeaById("idea-approve-1")?.status
        )
        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.IDEA_STATUS, pending.first().entity)
        assertEquals("upsert", pending.first().type)
        assertEquals("idea-approve-1", pending.first().entityId)
    }

    @Test
    fun given_apiOffline_when_convertIdea_then_updatesLocalItineraryAndQueuesConvertCommand() = runTest {
        // GIVEN
        val api = mockk<CoTripApi>()
        coEvery { api.convertIdeaToActivity(any(), any()) } throws IOException("offline")
        val tripId = "trip-convert-1"
        val dayId = "day-convert-1"
        val ideasStore = FakeIdeasCacheStore().apply {
            setIdeas(
                tripId = tripId,
                ideas = listOf(
                    IdeaDto(
                        id = "idea-convert-1",
                        tripId = tripId,
                        authorId = "user-1",
                        title = "Convert me",
                        city = "Paris",
                        link = "https://example.com",
                        costAmount = 20.0,
                        costType = "per_person",
                        notes = "note",
                        status = "pending",
                        updatedAt = "2026-01-01T00:00:00Z",
                        commentsCount = 0,
                    )
                )
            )
        }
        val itineraryStore = FakeItineraryCacheStore().apply {
            setItinerary(
                tripId = tripId,
                days = listOf(
                    ItineraryDayDto(
                        id = dayId,
                        tripId = tripId,
                        date = "2026-06-10",
                        dayNumber = 1,
                        city = "Paris",
                        cityProviderId = null,
                        cityLat = null,
                        cityLon = null,
                        isOutOfRange = false,
                        activities = emptyList(),
                    )
                )
            )
        }
        val repository = IdeaRepositoryImpl(
            api = api,
            syncQueueRepository = queue,
            ideasCacheStore = ideasStore,
            commentsCacheStore = FakeIdeaCommentsCacheStore(),
            itineraryCacheStore = itineraryStore,
            userCacheStore = FakeUserCacheStore(),
            networkStateProvider = networkStateProvider,
        )

        // WHEN
        repository.convertIdeaToActivity(
            ideaId = "idea-convert-1",
            request = ConvertIdeaRequest(dayId = dayId),
        )

        // THEN
        val activities = itineraryStore.getItinerary(tripId).first { it.id == dayId }.activities
        assertEquals(1, activities.size)
        assertEquals("idea-convert-1", activities.first().sourceIdeaId)
        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.IDEA_CONVERT, pending.first().entity)
        assertEquals("create", pending.first().type)
        assertEquals("idea-convert-1", pending.first().entityId)
    }

    @Test
    fun given_apiOffline_when_updateExpense_then_updatesLocalExpenseAndQueuesUpsert() = runTest {
        // GIVEN
        val api = mockk<CoTripApi>()
        coEvery { api.updateExpense(any(), any()) } throws IOException("offline")
        val expensesStore = FakeExpensesCacheStore().apply {
            setExpenses(
                tripId = "trip-expense-update",
                expenses = listOf(
                    ExpenseDto(
                        id = "expense-1",
                        tripId = "trip-expense-update",
                        title = "Before",
                        amount = 10.0,
                        currencyCode = "EUR",
                        status = "planned",
                        splitType = "equally",
                        participants = listOf(
                            ExpenseParticipantDto(
                                userId = "user-1",
                                isIncluded = true,
                                isPaid = false,
                            )
                        ),
                    )
                )
            )
        }
        val repository = ExpenseRepositoryImpl(
            api = api,
            syncQueueRepository = queue,
            expensesCacheStore = expensesStore,
            networkStateProvider = networkStateProvider,
        )

        // WHEN
        repository.updateExpense(
            expenseId = "expense-1",
            request = ExpenseUpdateRequest(title = "After"),
        )

        // THEN
        assertEquals("After", expensesStore.findExpenseById("expense-1")?.title)
        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.EXPENSE, pending.first().entity)
        assertEquals("upsert", pending.first().type)
        assertEquals("expense-1", pending.first().entityId)
    }

    @Test
    fun given_apiOffline_when_trimOutOfRange_then_updatesLocalItineraryAndQueuesCommand() = runTest {
        // GIVEN
        val api = mockk<CoTripApi>()
        coEvery { api.trimOutOfRangeDays(any(), any()) } throws IOException("offline")
        val tripId = "trip-trim-1"
        val itineraryStore = FakeItineraryCacheStore().apply {
            setItinerary(
                tripId = tripId,
                days = listOf(
                    ItineraryDayDto(
                        id = "day-trim-1",
                        tripId = tripId,
                        date = "2026-06-10",
                        dayNumber = 1,
                        city = null,
                        cityProviderId = null,
                        cityLat = null,
                        cityLon = null,
                        isOutOfRange = false,
                        activities = emptyList(),
                    ),
                    ItineraryDayDto(
                        id = "day-trim-2",
                        tripId = tripId,
                        date = "2026-06-11",
                        dayNumber = 2,
                        city = null,
                        cityProviderId = null,
                        cityLat = null,
                        cityLon = null,
                        isOutOfRange = true,
                        activities = emptyList(),
                    ),
                )
            )
        }
        val repository = ItineraryRepositoryImpl(
            api = api,
            syncQueueRepository = queue,
            itineraryCacheStore = itineraryStore,
            networkStateProvider = networkStateProvider,
        )

        // WHEN
        repository.trimOutOfRange(
            tripId = tripId,
            request = TrimOutOfRangeRequest(
                action = "remove",
                dayIds = listOf("day-trim-2"),
            )
        )

        // THEN
        assertEquals(1, itineraryStore.getItinerary(tripId).size)
        assertEquals("day-trim-1", itineraryStore.getItinerary(tripId).first().id)
        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.ITINERARY_TRIM, pending.first().entity)
        assertEquals("upsert", pending.first().type)
        assertEquals(tripId, pending.first().entityId)
    }

    @Test
    fun given_apiOffline_when_updateSettings_then_setsLocalSettingsAndQueuesUpsert() = runTest {
        // GIVEN
        val api = mockk<CoTripApi>()
        coEvery { api.updateNotificationSettings(any()) } throws IOException("offline")
        val settingsStore = FakeNotificationsCacheStore()
        val networkProvider = mockk<NetworkStateProvider>()
        every { networkProvider.isOnline() } returns true
        val repository = NotificationRepositoryImpl(
            api = api,
            notificationsCacheStore = settingsStore,
            syncQueueRepository = queue,
            networkStateProvider = networkProvider,
        )
        val settings = listOf(NotificationSettingDto(key = "ideas_comments", enabled = false))

        // WHEN
        val result = repository.updateSettings(settings)

        // THEN
        assertTrue(result.isSuccess)
        assertEquals(settings, settingsStore.getSettings())
        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.NOTIFICATION_SETTINGS, pending.first().entity)
        assertEquals("upsert", pending.first().type)
        assertEquals("me", pending.first().entityId)
    }

    @Test
    fun given_apiOffline_when_updateMe_then_setsLocalUserAndQueuesProfileUpsert() = runTest {
        // GIVEN
        val api = mockk<CoTripApi>()
        coEvery { api.updateMe(any()) } throws IOException("offline")
        val userStore = FakeUserCacheStore().apply {
            setUser(UserDto(id = "user-1", name = "Before Name", initials = "BN"))
        }
        val repository = UserRepositoryImpl(
            api = api,
            sessionCleaner = mockk<SessionCleaner>(relaxed = true),
            userCacheStore = userStore,
            pushTokenSyncManager = mockk<PushTokenSyncManager>(relaxed = true),
            syncQueueRepository = queue,
        )

        // WHEN
        val updated = repository.updateMe(
            UpdateUserRequest(
                name = "After Name",
                photoUrl = "https://photo",
            )
        )

        // THEN
        assertEquals("user-1", updated.id)
        assertEquals("After Name", updated.name)
        assertEquals("After Name", userStore.getUser()?.name)
        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.USER_PROFILE, pending.first().entity)
        assertEquals("upsert", pending.first().type)
        assertEquals("me", pending.first().entityId)
    }

    @Test
    fun given_apiOffline_when_saveAiSuggestion_then_queuesSaveCommand() = runTest {
        // GIVEN
        val api = mockk<CoTripApi>()
        coEvery { api.saveAiSuggestionToIdeas(any()) } throws IOException("offline")
        val repository = AiSuggestionsRepositoryImpl(
            api = api,
            syncQueueRepository = queue,
        )

        // WHEN
        repository.saveSuggestionToIdeas("suggestion-1")

        // THEN
        val pending = database.syncChangeDao().listPending(10)
        assertEquals(1, pending.size)
        assertEquals(SyncEntities.AI_SUGGESTION_SAVE, pending.first().entity)
        assertEquals("upsert", pending.first().type)
        assertEquals("suggestion-1", pending.first().entityId)
    }

    private class NoOpSyncScheduler(context: Context) : SyncScheduler(context) {
        override fun schedule() = Unit
    }
}

private class FakeTripsCacheStore : TripsCacheStore {
    private val state = MutableStateFlow<List<TripDto>>(emptyList())
    override val trips: Flow<List<TripDto>> = state
    override suspend fun getTrips(): List<TripDto> = state.value
    override suspend fun setTrips(trips: List<TripDto>) {
        state.value = trips
    }

    override suspend fun upsertTrip(trip: TripDto) {
        val current = state.value.toMutableList()
        val index = current.indexOfFirst { it.id == trip.id }
        if (index >= 0) current[index] = trip else current.add(0, trip)
        state.value = current
    }

    override suspend fun removeTrip(tripId: String) {
        state.value = state.value.filterNot { it.id == tripId }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

private class FakeTripMembersCacheStore : TripMembersCacheStore {
    private val byTrip = MutableStateFlow<Map<String, List<MemberDto>>>(emptyMap())

    override fun observeMembers(tripId: String): Flow<List<MemberDto>> =
        byTrip.map { it[tripId].orEmpty() }

    override suspend fun getMembers(tripId: String): List<MemberDto> =
        byTrip.value[tripId].orEmpty()

    override suspend fun setMembers(tripId: String, members: List<MemberDto>) {
        byTrip.value = byTrip.value.toMutableMap().apply { put(tripId, members) }
    }

    override suspend fun removeMember(tripId: String, memberId: String) {
        val updated = byTrip.value[tripId].orEmpty().filterNot { it.userId == memberId }
        byTrip.value = byTrip.value.toMutableMap().apply { put(tripId, updated) }
    }

    override suspend fun clearTrip(tripId: String) {
        byTrip.value = byTrip.value.toMutableMap().apply { remove(tripId) }
    }

    override suspend fun clearAll() {
        byTrip.value = emptyMap()
    }
}

private class FakeItineraryCacheStore : ItineraryCacheStore {
    private val byTrip = MutableStateFlow<Map<String, List<ItineraryDayDto>>>(emptyMap())

    override fun observeItinerary(tripId: String): Flow<List<ItineraryDayDto>> =
        byTrip.map { it[tripId].orEmpty() }

    override suspend fun getItinerary(tripId: String): List<ItineraryDayDto> =
        byTrip.value[tripId].orEmpty()

    override suspend fun getAll(): Map<String, List<ItineraryDayDto>> = byTrip.value
    override suspend fun setItinerary(tripId: String, days: List<ItineraryDayDto>) {
        byTrip.value = byTrip.value.toMutableMap().apply { put(tripId, days) }
    }

    override suspend fun updateItinerary(
        tripId: String,
        transform: (List<ItineraryDayDto>) -> List<ItineraryDayDto>
    ) {
        val updated = transform(byTrip.value[tripId].orEmpty())
        byTrip.value = byTrip.value.toMutableMap().apply { put(tripId, updated) }
    }

    override suspend fun clearTrip(tripId: String) {
        byTrip.value = byTrip.value.toMutableMap().apply { remove(tripId) }
    }

    override suspend fun clearAll() {
        byTrip.value = emptyMap()
    }
}

private class FakeUserCacheStore : UserCacheStore {
    private val state = MutableStateFlow<UserDto?>(null)
    override val user: Flow<UserDto?> = state
    override suspend fun getUser(): UserDto? = state.value
    override suspend fun setUser(user: UserDto?) {
        state.value = user
    }

    override suspend fun clear() {
        state.value = null
    }
}

private class FakeIdeasCacheStore : IdeasCacheStore {
    private val byTrip = MutableStateFlow<Map<String, List<IdeaDto>>>(emptyMap())

    override fun observeIdeas(tripId: String): Flow<List<IdeaDto>> =
        byTrip.map { it[tripId].orEmpty() }

    override fun observeIdeaById(ideaId: String): Flow<IdeaDto?> = byTrip.map { map ->
        map.values.asSequence().flatten().firstOrNull { it.id == ideaId }
    }

    override suspend fun getIdeas(tripId: String): List<IdeaDto> = byTrip.value[tripId].orEmpty()
    override suspend fun findIdeaById(ideaId: String): IdeaDto? =
        byTrip.value.values.asSequence().flatten().firstOrNull { it.id == ideaId }

    override suspend fun setIdeas(tripId: String, ideas: List<IdeaDto>) {
        byTrip.value = byTrip.value.toMutableMap().apply { put(tripId, ideas) }
    }

    override suspend fun upsertIdea(tripId: String, idea: IdeaDto) {
        val current = byTrip.value[tripId].orEmpty().toMutableList()
        val index = current.indexOfFirst { it.id == idea.id }
        if (index >= 0) current[index] = idea else current.add(0, idea)
        byTrip.value = byTrip.value.toMutableMap().apply { put(tripId, current) }
    }

    override suspend fun removeIdea(tripId: String, ideaId: String) {
        val updated = byTrip.value[tripId].orEmpty().filterNot { it.id == ideaId }
        byTrip.value = byTrip.value.toMutableMap().apply { put(tripId, updated) }
    }

    override suspend fun clearTrip(tripId: String) {
        byTrip.value = byTrip.value.toMutableMap().apply { remove(tripId) }
    }

    override suspend fun clearAll() {
        byTrip.value = emptyMap()
    }
}

private class FakeIdeaCommentsCacheStore : IdeaCommentsCacheStore {
    private val byIdea = MutableStateFlow<Map<String, List<CommentDto>>>(emptyMap())
    override fun observeComments(ideaId: String): Flow<List<CommentDto>> =
        byIdea.map { it[ideaId].orEmpty() }

    override suspend fun getComments(ideaId: String): List<CommentDto> =
        byIdea.value[ideaId].orEmpty()

    override suspend fun setComments(ideaId: String, comments: List<CommentDto>) {
        byIdea.value = byIdea.value.toMutableMap().apply { put(ideaId, comments) }
    }

    override suspend fun clearIdea(ideaId: String) {
        byIdea.value = byIdea.value.toMutableMap().apply { remove(ideaId) }
    }

    override suspend fun clearAll() {
        byIdea.value = emptyMap()
    }
}

private class FakeExpensesCacheStore : ExpensesCacheStore {
    private val byTrip = MutableStateFlow<Map<String, List<ExpenseDto>>>(emptyMap())
    override fun observeExpenses(tripId: String): Flow<List<ExpenseDto>> =
        byTrip.map { it[tripId].orEmpty() }

    override fun observeExpenseById(expenseId: String): Flow<ExpenseDto?> = byTrip.map { map ->
        map.values.asSequence().flatten().firstOrNull { it.id == expenseId }
    }

    override suspend fun getExpenses(tripId: String): List<ExpenseDto> =
        byTrip.value[tripId].orEmpty()

    override suspend fun findExpenseById(expenseId: String): ExpenseDto? =
        byTrip.value.values.asSequence().flatten().firstOrNull { it.id == expenseId }

    override suspend fun setExpenses(tripId: String, expenses: List<ExpenseDto>) {
        byTrip.value = byTrip.value.toMutableMap().apply { put(tripId, expenses) }
    }

    override suspend fun upsertExpense(tripId: String, expense: ExpenseDto) {
        val current = byTrip.value[tripId].orEmpty().toMutableList()
        val index = current.indexOfFirst { it.id == expense.id }
        if (index >= 0) current[index] = expense else current.add(0, expense)
        byTrip.value = byTrip.value.toMutableMap().apply { put(tripId, current) }
    }

    override suspend fun removeExpense(tripId: String, expenseId: String) {
        val updated = byTrip.value[tripId].orEmpty().filterNot { it.id == expenseId }
        byTrip.value = byTrip.value.toMutableMap().apply { put(tripId, updated) }
    }

    override suspend fun clearTrip(tripId: String) {
        byTrip.value = byTrip.value.toMutableMap().apply { remove(tripId) }
    }

    override suspend fun clearAll() {
        byTrip.value = emptyMap()
    }
}

private class FakeNotificationsCacheStore : NotificationsCacheStore {
    private val notificationsState = MutableStateFlow<List<NotificationDto>>(emptyList())
    private val settingsState = MutableStateFlow<List<NotificationSettingDto>>(emptyList())

    override val notifications: Flow<List<NotificationDto>> = notificationsState
    override val settings: Flow<List<NotificationSettingDto>> = settingsState

    override suspend fun getNotifications(): List<NotificationDto> = notificationsState.value

    override suspend fun getSettings(): List<NotificationSettingDto> = settingsState.value

    override suspend fun setNotifications(items: List<NotificationDto>) {
        notificationsState.value = items
    }

    override suspend fun markRead(notificationId: String) {
        notificationsState.value = notificationsState.value.map { notification ->
            if (notification.id == notificationId) {
                notification.copy(readAt = "2026-01-01T00:00:00Z")
            } else {
                notification
            }
        }
    }

    override suspend fun markReadBulkNonComment() {
        notificationsState.value = notificationsState.value.map {
            it.copy(readAt = "2026-01-01T00:00:00Z")
        }
    }

    override suspend fun markReadBulkIdeaComments(ideaId: String) {
        notificationsState.value = notificationsState.value.map {
            it.copy(readAt = "2026-01-01T00:00:00Z")
        }
    }

    override suspend fun setSettings(items: List<NotificationSettingDto>) {
        settingsState.value = items
    }

    override suspend fun clear() {
        notificationsState.value = emptyList()
        settingsState.value = emptyList()
    }
}
