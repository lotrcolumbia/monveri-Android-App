package co.monveri.register.data.repository

/**
 * Domain model for a single expense/receipt row. Mirrors iOS's `UploadedExpense` minus the fields
 * this Android slice's UI doesn't surface (`created_by`/`approved_by` ids — the audit timeline's
 * `actorName` covers "who approved this" for the detail screen without needing a raw id here).
 */
data class Expense(
    val id: Long,
    val clientUuid: String?,
    val vendorName: String,
    val expenseDate: String,
    val amountCents: Long,
    val taxAmountCents: Long?,
    val subtotalAmountCents: Long?,
    val categoryId: Long?,
    val categoryName: String?,
    val paymentMethod: ExpensePaymentMethod,
    val notes: String,
    val status: ExpenseStatus,
    val reviewedAt: String?,
    val rejectionReason: String?,
    val imageUrl: String?,
    val createdAt: String?,
)

enum class ExpenseStatus(val label: String) {
    PENDING("Pending"),
    APPROVED("Approved"),
    REJECTED("Needs attention"),
    ;

    companion object {
        /** Anything other than `approved`/`rejected` is `pending` — matches iOS's `default:` case. */
        fun fromWire(value: String): ExpenseStatus = when (value) {
            "approved" -> APPROVED
            "rejected" -> REJECTED
            else -> PENDING
        }
    }
}

enum class ExpensePaymentMethod(val wireValue: String, val label: String) {
    CASH("cash", "Cash"),
    CREDIT("credit", "Credit card"),
    DEBIT("debit", "Debit card"),
    CHECK("check", "Check"),
    OTHER("other", "Other"),
    ;

    companion object {
        fun fromWire(value: String): ExpensePaymentMethod = entries.firstOrNull { it.wireValue == value } ?: OTHER
    }
}

/** A category from `expense_categories` — store-editable data, not a fixed enum. */
data class ExpenseCategory(
    val id: Long,
    val name: String,
    val color: String?,
)

data class ExpenseAuditEntry(
    val id: Long,
    val action: String,
    val detail: String?,
    val createdAt: String,
    val actorName: String,
)

data class ExpenseDetail(
    val expense: Expense,
    val audit: List<ExpenseAuditEntry>,
)

data class ExpensePage(
    val items: List<Expense>,
    val page: Int,
    val hasMore: Boolean,
)

/**
 * What the manual-entry form collects. No receipt image in this slice — see
 * [co.monveri.register.network.MonveriApi.createExpense]'s doc for why.
 */
data class NewExpense(
    val vendorName: String,
    val expenseDate: String,
    val amountCents: Long,
    val taxAmountCents: Long?,
    val subtotalAmountCents: Long?,
    val categoryId: Long,
    val paymentMethod: ExpensePaymentMethod,
    val notes: String,
)
