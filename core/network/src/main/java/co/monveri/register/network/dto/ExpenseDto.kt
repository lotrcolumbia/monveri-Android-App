package co.monveri.register.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format from `serializeExpenseRow()` (`api/register/expenses/_helpers.php`) — the same
 * shape iOS decodes as `UploadedExpense`. Amounts arrive as strings (server casts them to avoid
 * float drift through JSON), so callers convert via [co.monveri.register.data.repository] helpers,
 * never through `Double`.
 */
@Serializable
data class ExpenseDto(
    @SerialName("id") val id: Long,
    @SerialName("client_uuid") val clientUuid: String? = null,
    @SerialName("vendor_name") val vendorName: String = "",
    @SerialName("expense_date") val expenseDate: String? = null,
    @SerialName("amount") val amount: String = "0.00",
    @SerialName("tax_amount") val taxAmount: String? = null,
    @SerialName("subtotal_amount") val subtotalAmount: String? = null,
    @SerialName("category_id") val categoryId: Long? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("payment_method") val paymentMethod: String = "cash",
    @SerialName("notes") val notes: String = "",
    @SerialName("source") val source: String = "manual",
    @SerialName("status") val status: String = "pending",
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/** `GET expenses/categories.php` — active categories only, server-ordered by `sort_order, name`. */
@Serializable
data class ExpenseCategoryDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("color") val color: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class ExpenseCategoriesDto(
    @SerialName("categories") val categories: List<ExpenseCategoryDto> = emptyList(),
)

/** `GET expenses/list.php` — paginated, defaults to the calling employee's own submissions. */
@Serializable
data class ExpenseListDto(
    @SerialName("items") val items: List<ExpenseDto> = emptyList(),
    @SerialName("page") val page: Int = 1,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class ExpenseAuditEntryDto(
    @SerialName("id") val id: Long,
    @SerialName("action") val action: String,
    @SerialName("detail") val detail: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("actor_name") val actorName: String = "Unknown",
)

/** `GET expenses/get.php` — a single expense plus its audit timeline. */
@Serializable
data class ExpenseDetailDto(
    @SerialName("expense") val expense: ExpenseDto,
    @SerialName("audit") val audit: List<ExpenseAuditEntryDto> = emptyList(),
)

/** `POST expenses/create.php` response. A duplicate `client_uuid` retry returns the same shape. */
@Serializable
data class CreateExpenseResponseDto(
    @SerialName("expense") val expense: ExpenseDto,
)

/**
 * The `expense` multipart text part for `POST expenses/create.php`. `source` is always
 * `"android_scan"` or `"manual"` — the backend and iOS's shared model already reserve
 * `android_scan` for exactly this client (see `ExpenseSource` in the iOS core module).
 */
@Serializable
data class CreateExpenseRequest(
    @SerialName("client_uuid") val clientUuid: String,
    @SerialName("vendor_name") val vendorName: String,
    @SerialName("expense_date") val expenseDate: String,
    @SerialName("amount") val amount: String,
    @SerialName("tax_amount") val taxAmount: String? = null,
    @SerialName("subtotal_amount") val subtotalAmount: String? = null,
    @SerialName("category_id") val categoryId: Long,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("notes") val notes: String = "",
    @SerialName("ocr_raw_text") val ocrRawText: String = "",
    // No default: kotlinx.serialization's `encodeDefaults = false` (the app-wide Json config)
    // omits a property from the wire entirely when its value equals the declared default, and
    // the backend's own fallback for a missing `source` is `'ios_scan'` — wrong for this client.
    // Every caller must pass this explicitly so it's always on the wire.
    @SerialName("source") val source: String,
)
