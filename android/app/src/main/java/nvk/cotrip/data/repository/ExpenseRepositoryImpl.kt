package nvk.cotrip.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import nvk.cotrip.data.cache.ExpensesCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.NetworkStateProvider
import nvk.cotrip.data.network.dto.ExpenseCreateRequest
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.ExpenseUpdateRequest
import nvk.cotrip.data.network.requireSuccess
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository
import nvk.cotrip.util.AppLogger
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class ExpenseRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
    private val expensesCacheStore: ExpensesCacheStore,
    private val networkStateProvider: NetworkStateProvider,
) : ExpenseRepository {

    private companion object {
        private const val TAG = "ExpenseRepository"
    }

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun observeExpenses(tripId: String): Flow<List<ExpenseDto>> {
        return expensesCacheStore.observeExpenses(tripId)
    }

    override fun getExpense(expenseId: String): Flow<ExpenseDto> {
        if (networkStateProvider.isOnline()) {
            ioScope.launch {
                runCatching { api.getExpense(expenseId) }
                    .onSuccess { expense ->
                        safeLocalMutation("getExpense.upsertExpense(expenseId=$expenseId)") {
                            expensesCacheStore.upsertExpense(expense.tripId, expense)
                        }
                    }
                    .onFailure { error ->
                        AppLogger.w(
                            TAG,
                            "getExpense network fetch failed for expenseId=$expenseId",
                            error
                        )
                    }
            }
        }
        return expensesCacheStore.observeExpenseById(expenseId).mapNotNull { it }
    }

    override suspend fun createExpense(tripId: String, request: ExpenseCreateRequest): ExpenseDto {
        val expense = api.createExpense(tripId, request)
        safeLocalMutation("createExpense.upsertExpense(tripId=$tripId, expenseId=${expense.id})") {
            expensesCacheStore.upsertExpense(tripId, expense)
        }
        return expense
    }

    override suspend fun updateExpense(expenseId: String, request: ExpenseUpdateRequest) {
        val updated = try {
            api.updateExpense(expenseId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.EXPENSE, expenseId, request)
            return
        }
        safeLocalMutation("updateExpense.upsertExpense(expenseId=$expenseId)") {
            expensesCacheStore.upsertExpense(updated.tripId, updated)
        }
    }

    override suspend fun deleteExpense(expenseId: String) {
        val cachedTripId =
            runCatching { expensesCacheStore.findExpenseById(expenseId)?.tripId }.getOrNull()
        val expenseTripId = cachedTripId ?: runCatching { api.getExpense(expenseId).tripId }
            .onFailure { AppLogger.w(TAG, "deleteExpense prefetch failed for expenseId=$expenseId", it) }
            .getOrNull()
        try {
            api.deleteExpense(expenseId).requireSuccess()
        } catch (e: IOException) {
            syncQueueRepository.enqueueDelete(SyncEntities.EXPENSE, expenseId)
            return
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
            AppLogger.i(TAG, "deleteExpense got 404 for expenseId=$expenseId, treating as already deleted")
        }
        if (expenseTripId != null) {
            safeLocalMutation("deleteExpense.removeExpense(expenseId=$expenseId)") {
                expensesCacheStore.removeExpense(expenseTripId, expenseId)
            }
        }
    }

    override suspend fun refreshExpenses(tripId: String): Result<Unit> {
        return runCatching {
            val expenses = mutableListOf<ExpenseDto>()
            var cursor: String? = null
            do {
                val page = api.listExpenses(tripId = tripId, limit = 100, cursor = cursor)
                expenses += page.items
                cursor = page.nextCursor
            } while (cursor != null)
            safeLocalMutation("refreshExpenses.setExpenses(tripId=$tripId)") {
                expensesCacheStore.setExpenses(tripId, expenses)
            }
        }
    }
}
