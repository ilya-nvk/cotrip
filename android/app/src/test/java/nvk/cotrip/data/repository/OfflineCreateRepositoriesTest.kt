package nvk.cotrip.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import nvk.cotrip.data.cache.ExpensesCacheStore
import nvk.cotrip.data.cache.IdeaCommentsCacheStore
import nvk.cotrip.data.cache.IdeasCacheStore
import nvk.cotrip.data.cache.ItineraryCacheStore
import nvk.cotrip.data.cache.TripMembersCacheStore
import nvk.cotrip.data.cache.TripsCacheStore
import nvk.cotrip.data.cache.UserCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.ActivityDto
import nvk.cotrip.data.network.dto.CommentDto
import nvk.cotrip.data.network.dto.CreateActivityRequest
import nvk.cotrip.data.network.dto.CreateIdeaRequest
import nvk.cotrip.data.network.dto.CreateTripRequest
import nvk.cotrip.data.network.dto.ExpenseCreateRequest
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.ExpenseParticipantInput
import nvk.cotrip.data.network.dto.IdeaDto
import nvk.cotrip.data.network.dto.ItineraryDayDto
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UserDto
import nvk.cotrip.data.sync.CoTripDatabase
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import nvk.cotrip.data.sync.SyncScheduler
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
