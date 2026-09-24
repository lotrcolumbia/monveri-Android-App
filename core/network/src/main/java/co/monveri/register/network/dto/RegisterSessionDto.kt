package co.monveri.register.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenSessionRequest(
    @SerialName("employee_id") val employeeId: Long,
    @SerialName("employee_name") val employeeName: String,
    @SerialName("opening_cash") val openingCash: Double,
)

@Serializable
data class CloseSessionRequest(
    @SerialName("session_id") val sessionId: Long,
    @SerialName("closing_cash") val closingCash: Double,
    @SerialName("notes") val notes: String? = null,
    @SerialName("closing_breakdown") val closingBreakdown: ClosingBreakdownDto? = null,
)

/** Matches the web POS's `close.php` breakdown JSON exactly — same section/key names, so the
 * backend's `closing_breakdown` column stores one consistent shape regardless of client. */
@Serializable
data class ClosingBreakdownDto(
    @SerialName("bills") val bills: Map<String, Int>,
    @SerialName("coins") val coins: Map<String, Int>,
    @SerialName("rolls") val rolls: Map<String, Int>,
    @SerialName("straps") val straps: Map<String, Int>,
    @SerialName("totals") val totals: ClosingBreakdownTotalsDto,
)

@Serializable
data class ClosingBreakdownTotalsDto(
    @SerialName("bills_total") val billsTotal: Double,
    @SerialName("coins_total") val coinsTotal: Double,
    @SerialName("rolls_total") val rollsTotal: Double,
    @SerialName("straps_total") val strapsTotal: Double,
)

/** Response shape of `GET /session/current.php` — always a wrapper object, `session` may be null. */
@Serializable
data class CurrentSessionEnvelopeDto(
    @SerialName("session") val session: RegisterSessionDto? = null,
)

@Serializable
data class RegisterSessionDto(
    @SerialName("id") val id: Long,
    @SerialName("employee_id") val employeeId: Long,
    @SerialName("employee_name") val employeeName: String,
    @SerialName("opened_at") val openedAt: String,
    @SerialName("opening_cash") val openingCash: Double,
    @SerialName("status") val status: String,
    @SerialName("is_training") val isTraining: Boolean = false,
)

/**
 * Response shape shared by `preview_close.php` (report only, session stays open) and
 * `close.php` (report + the session is actually closed). Only the fields the Close Register
 * screen renders are modeled here — the backend's `summary` carries several more (gift
 * certificates, cash-drawer drops/pay-ins) that neither this screen nor iOS's surfaces.
 */
@Serializable
data class SessionReportDto(
    @SerialName("employee_name") val employeeName: String,
    @SerialName("opened_at") val openedAt: String,
    @SerialName("closed_at") val closedAt: String,
    @SerialName("opening_cash") val openingCash: Double,
    @SerialName("summary") val summary: SessionSummaryDto,
)

@Serializable
data class SessionSummaryDto(
    @SerialName("total_transactions") val totalTransactions: Int = 0,
    @SerialName("total_sales") val totalSales: Double = 0.0,
    @SerialName("cash_total") val cashTotal: Double = 0.0,
    @SerialName("credit_total") val creditTotal: Double = 0.0,
    @SerialName("refund_total") val refundTotal: Double = 0.0,
    @SerialName("net_sales") val netSales: Double = 0.0,
    @SerialName("expected_cash") val expectedCash: Double = 0.0,
)
