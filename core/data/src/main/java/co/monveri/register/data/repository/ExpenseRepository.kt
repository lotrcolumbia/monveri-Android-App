package co.monveri.register.data.repository

import co.monveri.register.network.NetworkResult

/**
 * Back Office Expenses — list/detail/create against the `api/register/expenses/` endpoints.
 * Always scoped to the signed-in employee's own submissions (the backend's `mine=1` default); see
 * [co.monveri.register.network.MonveriApi.listExpenses]'s doc.
 */
interface ExpenseRepository {
    suspend fun categories(): NetworkResult<List<ExpenseCategory>>
    suspend fun list(page: Int): NetworkResult<ExpensePage>
    suspend fun get(id: Long): NetworkResult<ExpenseDetail>
    suspend fun create(expense: NewExpense): NetworkResult<Expense>

    /** Resolves a server-relative `image_url` (e.g. `"expenses/image.php?id=42"`) to a full URL. */
    fun resolveImageUrl(relativeUrl: String): String
}
