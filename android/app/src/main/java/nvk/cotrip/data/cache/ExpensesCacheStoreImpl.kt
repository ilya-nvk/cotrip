package nvk.cotrip.data.cache

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nvk.cotrip.data.network.dto.ExpenseDto
import javax.inject.Inject

class ExpensesCacheStoreImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : ExpensesCacheStore {

    override fun observeExpenses(tripId: String): Flow<List<ExpenseDto>> {
        return dataStore.data.map { prefs ->
            decodeCache(prefs[EXPENSES_KEY]).byTrip[tripId].orEmpty()
        }
    }

    override suspend fun getExpenses(tripId: String): List<ExpenseDto> {
        val prefs = dataStore.data.first()
        return decodeCache(prefs[EXPENSES_KEY]).byTrip[tripId].orEmpty()
    }

    override suspend fun setExpenses(tripId: String, expenses: List<ExpenseDto>) {
        updateCache { cache ->
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { put(tripId, expenses) })
        }
    }

    override suspend fun upsertExpense(tripId: String, expense: ExpenseDto) {
        updateCache { cache ->
            val existing = cache.byTrip[tripId].orEmpty().toMutableList()
            val index = existing.indexOfFirst { it.id == expense.id }
            if (index >= 0) {
                existing[index] = expense
            } else {
                existing.add(0, expense)
            }
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { put(tripId, existing) })
        }
    }

    override suspend fun removeExpense(tripId: String, expenseId: String) {
        updateCache { cache ->
            val remaining = cache.byTrip[tripId].orEmpty().filterNot { it.id == expenseId }
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { put(tripId, remaining) })
        }
    }

    override suspend fun clearTrip(tripId: String) {
        updateCache { cache ->
            cache.copy(byTrip = cache.byTrip.toMutableMap().apply { remove(tripId) })
        }
    }

    private suspend fun updateCache(transform: (ExpensesCache) -> ExpensesCache) {
        dataStore.edit { prefs ->
            val current = decodeCache(prefs[EXPENSES_KEY])
            val updated = transform(current)
            prefs[EXPENSES_KEY] = json.encodeToString(ExpensesCache.serializer(), updated)
        }
    }

    private fun decodeCache(raw: String?): ExpensesCache {
        if (raw.isNullOrBlank()) return ExpensesCache()
        return runCatching { json.decodeFromString(ExpensesCache.serializer(), raw) }
            .getOrElse { ExpensesCache() }
    }

    @Serializable
    private data class ExpensesCache(
        val byTrip: Map<String, List<ExpenseDto>> = emptyMap(),
    )

    private companion object {
        private val EXPENSES_KEY = stringPreferencesKey("expenses_cache")
    }
}
