package nvk.cotrip.data.repository

import nvk.cotrip.data.network.dto.ExpenseCreateRequest
import nvk.cotrip.data.network.dto.ExpenseDto
import nvk.cotrip.data.network.dto.ExpenseUpdateRequest
import kotlinx.coroutines.flow.Flow

interface ExpenseRepository {
    fun observeExpenses(tripId: String): Flow<List<ExpenseDto>>
    suspend fun listExpenses(tripId: String): List<ExpenseDto>
    suspend fun getExpense(expenseId: String): ExpenseDto
    suspend fun createExpense(tripId: String, request: ExpenseCreateRequest): ExpenseDto
    suspend fun updateExpense(expenseId: String, request: ExpenseUpdateRequest)
    suspend fun deleteExpense(expenseId: String)
    suspend fun refreshExpenses(tripId: String): List<ExpenseDto>
}
