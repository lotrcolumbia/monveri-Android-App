package co.monveri.register.data.repository

import co.monveri.register.network.NetworkResult

/**
 * The till/drawer session lifecycle — separate from [co.monveri.register.data.AuthRepository]'s
 * PIN-auth session. A cashier can be signed in with no register open (e.g. the previous shift
 * closed it, or this is the first login of the day); [current] is how callers tell those two
 * states apart.
 */
interface RegisterSessionRepository {

    /** The open session for this (employee, device) pair, or `null` if there isn't one. */
    suspend fun current(): NetworkResult<RegisterSession?>

    /** Opens a new session with a starting cash count. Returns the new session id. */
    suspend fun open(employeeId: Long, employeeName: String, openingCashCents: Long): NetworkResult<Long>

    /** Same report [close] would produce, without actually closing the session. */
    suspend fun previewClose(): NetworkResult<SessionReport?>

    /** Closes the session and returns the final variance report. */
    suspend fun close(
        sessionId: Long,
        closingCashCents: Long,
        notes: String?,
        breakdown: CashCount? = null,
    ): NetworkResult<SessionReport?>
}

data class RegisterSession(
    val id: Long,
    val employeeName: String,
    val openedAt: String,
    val openingCashCents: Long,
)

/**
 * The Close Register variance report. Cents in [openingCashCents]/[cashTotalCents]/etc.; only
 * the fields the screen renders are modeled — the backend's summary carries several more
 * (cash-drawer drops/pay-ins, gift certificates) that iOS's own Close Register screen doesn't
 * surface either.
 */
data class SessionReport(
    val employeeName: String,
    val openedAt: String,
    val closedAt: String,
    val openingCashCents: Long,
    val totalTransactions: Int,
    val totalSalesCents: Long,
    val cashTotalCents: Long,
    val creditTotalCents: Long,
    val refundTotalCents: Long,
    val netSalesCents: Long,
    val expectedCashCents: Long,
)

/**
 * A physical cash-drawer count for register close — mirrors the web POS's `close.php` Bills/Loose
 * Coins/Coin Rolls/Bill Straps breakdown exactly (same denominations, same per-unit values), so
 * the same `closing_breakdown` JSON the browser sends is what Android sends too.
 */
data class CashCount(
    val bills100: Int = 0,
    val bills50: Int = 0,
    val bills20: Int = 0,
    val bills10: Int = 0,
    val bills5: Int = 0,
    val bills1: Int = 0,
    val dollarCoins: Int = 0,
    val halfDollars: Int = 0,
    val quarters: Int = 0,
    val dimes: Int = 0,
    val nickels: Int = 0,
    val pennies: Int = 0,
    val dollarRolls: Int = 0,
    val halfDollarRolls: Int = 0,
    val quarterRolls: Int = 0,
    val dimeRolls: Int = 0,
    val nickelRolls: Int = 0,
    val pennyRolls: Int = 0,
    val straps20: Int = 0,
    val straps10: Int = 0,
    val straps5: Int = 0,
    val straps1: Int = 0,
) {
    val billsCents: Long
        get() = bills100 * 10_000L + bills50 * 5_000L + bills20 * 2_000L + bills10 * 1_000L + bills5 * 500L + bills1 * 100L

    val coinsCents: Long
        get() = dollarCoins * 100L + halfDollars * 50L + quarters * 25L + dimes * 10L + nickels * 5L + pennies * 1L

    val rollsCents: Long
        get() = dollarRolls * 2_500L + halfDollarRolls * 1_000L + quarterRolls * 1_000L +
            dimeRolls * 500L + nickelRolls * 200L + pennyRolls * 50L

    val strapsCents: Long
        get() = straps20 * 50_000L + straps10 * 25_000L + straps5 * 10_000L + straps1 * 2_500L

    val totalCents: Long
        get() = billsCents + coinsCents + rollsCents + strapsCents
}
