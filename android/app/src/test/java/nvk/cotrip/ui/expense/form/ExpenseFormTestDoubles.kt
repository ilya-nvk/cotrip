package nvk.cotrip.ui.expense.form

import androidx.navigation.NavOptionsBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import nvk.cotrip.data.network.dto.ExpenseCreateRequest
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.ExpenseParticipantDto
import nvk.cotrip.data.network.dto.ExpenseUpdateRequest
import nvk.cotrip.data.network.dto.MemberDto
import nvk.cotrip.data.network.dto.TripDto
import nvk.cotrip.data.network.dto.UserDto
import nvk.cotrip.data.repository.ExpenseRepository
import nvk.cotrip.data.repository.TripRepository
import nvk.cotrip.data.repository.UserRepository
import nvk.cotrip.ui.navigation.AppNavigator
import nvk.cotrip.ui.navigation.Destination
import java.time.LocalDate

internal class ExpenseFormFakeNavigator : AppNavigator {
    val destinations = mutableListOf<Destination>()
    var popCalls: Int = 0

    override fun navigate(
        destination: Destination,
        navOptions: (NavOptionsBuilder.() -> Unit)?,
    ) {
        destinations += destination
    }

    override fun popBackStack(): Boolean {
        popCalls += 1
        return true
    }
}

internal class ExpenseFormFakeTripRepository(
    trip: TripDto,
    members: List<MemberDto> = emptyList(),
) : TripRepository {
    private val tripFlow = MutableStateFlow(trip)
    private val membersFlow = MutableStateFlow(members)

    var getTripError: Throwable? = null

    override val trips: MutableStateFlow<List<TripDto>> = MutableStateFlow(listOf(trip))

    override fun getTrip(tripId: String): Flow<TripDto> = flow {
        getTripError?.let { throw it }
        emit(tripFlow.value)
    }

    override suspend fun refreshTrips(): Result<Unit> = Result.success(Unit)

    override suspend fun createTrip(request: nvk.cotrip.data.network.dto.CreateTripRequest): String = "trip-1"

    override suspend fun updateTrip(tripId: String, request: nvk.cotrip.data.network.dto.UpdateTripRequest): Result<Unit> =
        Result.success(Unit)

    override suspend fun archiveTrip(tripId: String) = Unit

    override suspend fun deleteTrip(tripId: String) = Unit

    override fun tripMembers(tripId: String): Flow<List<MemberDto>> = membersFlow

    override suspend fun removeMember(tripId: String, memberId: String) = Unit
}

internal class ExpenseFormFakeExpenseRepository(
    expense: ExpenseDto? = null,
    initialExpensesList: List<ExpenseDto>? = null,
) : ExpenseRepository {
    private val expenseFlow = MutableStateFlow(expense)
    private val expensesListFlow = MutableStateFlow(
        initialExpensesList ?: expense?.let { listOf(it) }.orEmpty()
    )

    var createExpenseResult: ExpenseDto? = null
    var createExpenseToThrow: Throwable? = null
    var updateExpenseToThrow: Throwable? = null
    var deleteExpenseToThrow: Throwable? = null
    var getExpenseToThrow: Throwable? = null

    val createExpenseCalls = mutableListOf<Pair<String, ExpenseCreateRequest>>()
    val updateExpenseCalls = mutableListOf<Pair<String, ExpenseUpdateRequest>>()
    val deleteExpenseCalls = mutableListOf<String>()

    override fun observeExpenses(tripId: String): Flow<List<ExpenseDto>> = expensesListFlow

    override fun getExpense(expenseId: String): Flow<ExpenseDto> = flow {
        getExpenseToThrow?.let { throw it }
        emit(expenseFlow.value ?: expenseFormExpenseDto(id = expenseId, amount = 0.0))
    }

    override suspend fun createExpense(tripId: String, request: ExpenseCreateRequest): ExpenseDto {
        createExpenseToThrow?.let { throw it }
        createExpenseCalls += tripId to request
        return createExpenseResult ?: expenseFormExpenseDto(
            id = "expense-created",
            tripId = tripId,
            amount = request.amount,
            title = request.title,
        )
    }

    override suspend fun updateExpense(expenseId: String, request: ExpenseUpdateRequest) {
        updateExpenseToThrow?.let { throw it }
        updateExpenseCalls += expenseId to request
    }

    override suspend fun deleteExpense(expenseId: String) {
        deleteExpenseToThrow?.let { throw it }
        deleteExpenseCalls += expenseId
    }

    override suspend fun refreshExpenses(tripId: String): Result<Unit> = Result.success(Unit)

    fun setExpense(expense: ExpenseDto) {
        expenseFlow.value = expense
    }

    fun setExpenses(expenses: List<ExpenseDto>) {
        expensesListFlow.value = expenses
    }
}

internal class ExpenseFormFakeUserRepository(
    initialMe: UserDto?,
) : UserRepository {
    override val me: MutableStateFlow<UserDto?> = MutableStateFlow(initialMe)

    override suspend fun refreshMe(): Result<Unit> = Result.success(Unit)

    override suspend fun updateMe(request: nvk.cotrip.data.network.dto.UpdateUserRequest): UserDto =
        me.value ?: expenseFormUserDto(id = "user-unknown")

    override suspend fun deleteMe() = Unit

    override fun clearSession() = Unit
}

internal fun expenseFormTripDto(
    id: String = "trip-1",
    currencyCode: String = "EUR",
): TripDto = TripDto(
    id = id,
    ownerId = "owner-1",
    title = "Test Trip",
    description = null,
    startDate = LocalDate.now().toString(),
    endDate = LocalDate.now().plusDays(3).toString(),
    locationLine = null,
    coverUrl = null,
    currencyCode = currencyCode,
    status = "active",
    updatedAt = "2026-03-16T10:00:00Z",
)

internal fun expenseFormMemberDto(
    id: String,
    initials: String = id.take(2).uppercase(),
    name: String = "User $id",
): MemberDto = MemberDto(
    userId = id,
    name = name,
    photoUrl = null,
    initials = initials,
    role = "member",
    status = "accepted",
)

internal fun expenseFormExpenseDto(
    id: String,
    tripId: String = "trip-1",
    title: String = "Expense $id",
    amount: Double,
    status: String = "paid",
    paidById: String? = "user-1",
    date: String? = LocalDate.now().toString(),
    splitType: String = "equally",
    note: String? = null,
    participants: List<ExpenseParticipantDto> = emptyList(),
): ExpenseDto = ExpenseDto(
    id = id,
    tripId = tripId,
    title = title,
    amount = amount,
    currencyCode = "EUR",
    status = status,
    paidById = paidById,
    date = date,
    splitType = splitType,
    note = note,
    participants = participants,
)

internal fun expenseFormUserDto(
    id: String,
    name: String = "User $id",
): UserDto = UserDto(
    id = id,
    name = name,
    photoUrl = null,
    initials = name.take(2).uppercase(),
)
