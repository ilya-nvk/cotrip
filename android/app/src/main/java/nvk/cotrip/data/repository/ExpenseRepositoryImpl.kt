package nvk.cotrip.data.repository

import java.io.IOException
import javax.inject.Inject
import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.ExpenseCreateRequest
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.ExpenseUpdateRequest
import nvk.cotrip.data.sync.SyncEntities
import nvk.cotrip.data.sync.SyncQueueRepository

class ExpenseRepositoryImpl @Inject constructor(
    private val api: CoTripApi,
    private val syncQueueRepository: SyncQueueRepository,
) : ExpenseRepository {
    override suspend fun listExpenses(tripId: String): List<ExpenseDto> {
        return api.listExpenses(tripId).items
    }

    override suspend fun getExpense(expenseId: String): ExpenseDto {
        return api.getExpense(expenseId)
    }

    override suspend fun createExpense(tripId: String, request: ExpenseCreateRequest): ExpenseDto {
        return api.createExpense(tripId, request)
    }

    override suspend fun updateExpense(expenseId: String, request: ExpenseUpdateRequest) {
        try {
            api.updateExpense(expenseId, request)
        } catch (e: IOException) {
            syncQueueRepository.enqueueUpsert(SyncEntities.EXPENSE, expenseId, request)
        }
    }

    override suspend fun deleteExpense(expenseId: String) {
        try {
            api.deleteExpense(expenseId)
        } catch (e: IOException) {
            syncQueueRepository.enqueueDelete(SyncEntities.EXPENSE, expenseId)
        }
    }

    override suspend fun refreshExpenses(tripId: String): List<ExpenseDto> {
        return listExpenses(tripId)
    }
}
