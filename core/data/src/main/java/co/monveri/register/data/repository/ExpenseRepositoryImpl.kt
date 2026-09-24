package co.monveri.register.data.repository

import co.monveri.register.network.BaseUrlProvider
import co.monveri.register.network.MonveriApi
import co.monveri.register.network.NetworkError
import co.monveri.register.network.NetworkErrorMapper
import co.monveri.register.network.NetworkResult
import co.monveri.register.network.dto.CreateExpenseRequest
import co.monveri.register.network.dto.ExpenseCategoryDto
import co.monveri.register.network.dto.ExpenseDto
import co.monveri.register.network.runCatchingNetwork
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val api: MonveriApi,
    private val errorMapper: NetworkErrorMapper,
    private val json: Json,
    private val baseUrlProvider: BaseUrlProvider,
) : ExpenseRepository {

    override suspend fun categories(): NetworkResult<List<ExpenseCategory>> {
        val result = runCatchingNetwork(errorMapper) { api.expenseCategories() }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val payload = result.data.data
                if (!result.data.success || payload == null) {
                    NetworkResult.Failure(serverError(result.data.message, "Could not load categories"))
                } else {
                    NetworkResult.Success(payload.categories.map { it.toDomain() })
                }
            }
        }
    }

    override suspend fun list(page: Int): NetworkResult<ExpensePage> {
        val result = runCatchingNetwork(errorMapper) { api.listExpenses(page = page) }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val payload = result.data.data
                if (!result.data.success || payload == null) {
                    NetworkResult.Failure(serverError(result.data.message, "Could not load expenses"))
                } else {
                    NetworkResult.Success(
                        ExpensePage(
                            items = payload.items.map { it.toDomain() },
                            page = payload.page,
                            hasMore = payload.hasMore,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun get(id: Long): NetworkResult<ExpenseDetail> {
        val result = runCatchingNetwork(errorMapper) { api.getExpense(id) }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val payload = result.data.data
                if (!result.data.success || payload == null) {
                    NetworkResult.Failure(serverError(result.data.message, "Could not load expense"))
                } else {
                    NetworkResult.Success(
                        ExpenseDetail(
                            expense = payload.expense.toDomain(),
                            audit = payload.audit.map { entry ->
                                ExpenseAuditEntry(
                                    id = entry.id,
                                    action = entry.action,
                                    detail = entry.detail,
                                    createdAt = entry.createdAt,
                                    actorName = entry.actorName,
                                )
                            },
                        ),
                    )
                }
            }
        }
    }

    override suspend fun create(expense: NewExpense): NetworkResult<Expense> {
        val request = CreateExpenseRequest(
            clientUuid = UUID.randomUUID().toString(),
            vendorName = expense.vendorName,
            expenseDate = expense.expenseDate,
            amount = toDollarsString(expense.amountCents),
            taxAmount = expense.taxAmountCents?.let { toDollarsString(it) },
            subtotalAmount = expense.subtotalAmountCents?.let { toDollarsString(it) },
            categoryId = expense.categoryId,
            paymentMethod = expense.paymentMethod.wireValue,
            notes = expense.notes,
            source = "manual",
        )
        val body = json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE)

        val result = runCatchingNetwork(errorMapper) { api.createExpense(body) }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val payload = result.data.data
                if (!result.data.success || payload == null) {
                    NetworkResult.Failure(serverError(result.data.message, "Could not save expense"))
                } else {
                    NetworkResult.Success(payload.expense.toDomain())
                }
            }
        }
    }

    override fun resolveImageUrl(relativeUrl: String): String = baseUrlProvider.baseUrl() + relativeUrl

    private fun serverError(message: String?, fallback: String) =
        NetworkError.Server(MAX_HTTP_CODE, message ?: fallback)

    private companion object {
        const val MAX_HTTP_CODE: Int = 500
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

private fun ExpenseDto.toDomain(): Expense = Expense(
    id = id,
    clientUuid = clientUuid,
    vendorName = vendorName,
    expenseDate = expenseDate.orEmpty(),
    amountCents = toCents(amount),
    taxAmountCents = taxAmount?.let { toCents(it) },
    subtotalAmountCents = subtotalAmount?.let { toCents(it) },
    categoryId = categoryId,
    categoryName = categoryName,
    paymentMethod = ExpensePaymentMethod.fromWire(paymentMethod),
    notes = notes,
    status = ExpenseStatus.fromWire(status),
    reviewedAt = reviewedAt,
    rejectionReason = rejectionReason,
    imageUrl = imageUrl,
    createdAt = createdAt,
)

private fun ExpenseCategoryDto.toDomain(): ExpenseCategory = ExpenseCategory(id = id, name = name, color = color)

/** Server sends decimal-dollar strings (e.g. `"18.80"`) — parse via BigDecimal, never `Double`. */
private fun toCents(dollars: String): Long =
    BigDecimal(dollars).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

private fun toDollarsString(cents: Long): String =
    BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()
