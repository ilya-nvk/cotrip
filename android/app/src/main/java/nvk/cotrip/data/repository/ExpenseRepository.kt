package nvk.cotrip.data.repository

import nvk.cotrip.data.network.CoTripApi
import nvk.cotrip.data.network.dto.ExpenseCreateRequest
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.ExpenseUpdateRequest
import javax.inject.Inject

class ExpenseRepository @Inject constructor(
    private val api: CoTripApi,
) {
    suspend fun listExpenses(tripId: String): List<ExpenseDto> {
        return api.listExpenses(tripId).items
    }

    suspend fun getExpense(expenseId: String): ExpenseDto {
        return api.getExpense(expenseId)
    }

    suspend fun createExpense(tripId: String, request: ExpenseCreateRequest): ExpenseDto {
        return api.createExpense(tripId, request)
    }

    suspend fun updateExpense(expenseId: String, request: ExpenseUpdateRequest): ExpenseDto {
        return api.updateExpense(expenseId, request)
    }

    suspend fun deleteExpense(expenseId: String) {
        api.deleteExpense(expenseId)
    }

    suspend fun refreshExpenses(tripId: String): List<ExpenseDto> {
        return listExpenses(tripId)
    }
}
