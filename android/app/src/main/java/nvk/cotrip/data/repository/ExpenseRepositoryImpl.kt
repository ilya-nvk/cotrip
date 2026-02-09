package nvk.cotrip.data.repository

import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.cache.ExpensesCacheStore
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.ExpenseCreateRequest
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.ExpenseUpdateRequest
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository

class ExpenseRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
    private val expensesCacheStore: ExpensesCacheStore,
) : ExpenseRepository {
    override fun observeExpenses(tripId: String): Flow<List<ExpenseDto>> {
        return expensesCacheStore.observeExpenses(tripId)
    }

    override suspend fun listExpenses(tripId: String): List<ExpenseDto> {
        val cached = expensesCacheStore.getExpenses(tripId)
        if (cached.isNotEmpty()) return cached
        return refreshExpenses(tripId)
    }

    override suspend fun getExpense(expenseId: String): ExpenseDto {
        return api.getExpense(expenseId)
    }

    override suspend fun createExpense(tripId: String, request: ExpenseCreateRequest): ExpenseDto {
        val expense = api.createExpense(tripId, request)
        expensesCacheStore.upsertExpense(tripId, expense)
        return expense
    }

    override suspend fun updateExpense(expenseId: String, request: ExpenseUpdateRequest) {
        try {
            val updated = api.updateExpense(expenseId, request)
            expensesCacheStore.upsertExpense(updated.tripId, updated)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.EXPENSE, expenseId, request)
        }
    }

    override suspend fun deleteExpense(expenseId: String) {
        try {
            val expense = api.getExpense(expenseId)
            api.deleteExpense(expenseId)
            expensesCacheStore.removeExpense(expense.tripId, expenseId)
        } catch (e: IOException) {
            syncQueueRepository.enqueueDelete(SyncEntities.EXPENSE, expenseId)
        }
    }

    override suspend fun refreshExpenses(tripId: String): List<ExpenseDto> {
        val expenses = api.listExpenses(tripId).items
        expensesCacheStore.setExpenses(tripId, expenses)
        return expenses
    }
}
