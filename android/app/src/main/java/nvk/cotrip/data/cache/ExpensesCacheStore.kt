package nvk.cotrip.data.cache

import kotlinx.coroutines.flow.Flow
import nvk.cotrip.data.network.dto.ExpenseDto

interface ExpensesCacheStore {
    fun observeExpenses(tripId: String): Flow<List<ExpenseDto>>
    suspend fun getExpenses(tripId: String): List<ExpenseDto>
    suspend fun setExpenses(tripId: String, expenses: List<ExpenseDto>)
    suspend fun upsertExpense(tripId: String, expense: ExpenseDto)
    suspend fun removeExpense(tripId: String, expenseId: String)
    suspend fun clearTrip(tripId: String)
}
