package co.monveri.register.data.repository

import co.monveri.register.network.MonveriApi
import co.monveri.register.network.NetworkError
import co.monveri.register.network.NetworkErrorMapper
import co.monveri.register.network.NetworkResult
import co.monveri.register.network.dto.ClosingBreakdownDto
import co.monveri.register.network.dto.ClosingBreakdownTotalsDto
import co.monveri.register.network.dto.CloseSessionRequest
import co.monveri.register.network.dto.OpenSessionRequest
import co.monveri.register.network.dto.RegisterSessionDto
import co.monveri.register.network.dto.SessionReportDto
import co.monveri.register.network.runCatchingNetwork
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegisterSessionRepositoryImpl @Inject constructor(
    private val api: MonveriApi,
    private val errorMapper: NetworkErrorMapper,
) : RegisterSessionRepository {

    override suspend fun current(): NetworkResult<RegisterSession?> {
        val result = runCatchingNetwork(errorMapper) { api.currentRegisterSession() }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                if (!envelope.success) {
                    NetworkResult.Failure(
                        NetworkError.Server(MAX_HTTP_CODE, envelope.message ?: "Failed to load register session"),
                    )
                } else {
                    NetworkResult.Success(envelope.data?.session?.toDomain())
                }
            }
        }
    }

    override suspend fun open(
        employeeId: Long,
        employeeName: String,
        openingCashCents: Long,
    ): NetworkResult<Long> {
        val result = runCatchingNetwork(errorMapper) {
            api.openRegisterSession(
                OpenSessionRequest(
                    employeeId = employeeId,
                    employeeName = employeeName,
                    openingCash = toDollars(openingCashCents),
                ),
            )
        }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                val sessionId = envelope.data
                if (!envelope.success || sessionId == null) {
                    NetworkResult.Failure(
                        NetworkError.Server(MAX_HTTP_CODE, envelope.message ?: "Could not open register"),
                    )
                } else {
                    NetworkResult.Success(sessionId)
                }
            }
        }
    }

    override suspend fun previewClose(): NetworkResult<SessionReport?> {
        val result = runCatchingNetwork(errorMapper) { api.previewCloseRegisterSession() }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                if (!envelope.success) {
                    NetworkResult.Failure(
                        NetworkError.Server(MAX_HTTP_CODE, envelope.message ?: "Could not load register preview"),
                    )
                } else {
                    NetworkResult.Success(envelope.data?.toDomain())
                }
            }
        }
    }

    override suspend fun close(
        sessionId: Long,
        closingCashCents: Long,
        notes: String?,
        breakdown: CashCount?,
    ): NetworkResult<SessionReport?> {
        val result = runCatchingNetwork(errorMapper) {
            api.closeRegisterSession(
                CloseSessionRequest(
                    sessionId = sessionId,
                    closingCash = toDollars(closingCashCents),
                    notes = notes,
                    closingBreakdown = breakdown?.toDto(),
                ),
            )
        }
        return when (result) {
            is NetworkResult.Failure -> result
            is NetworkResult.Success -> {
                val envelope = result.data
                if (!envelope.success) {
                    NetworkResult.Failure(
                        NetworkError.Server(MAX_HTTP_CODE, envelope.message ?: "Could not close register"),
                    )
                } else {
                    NetworkResult.Success(envelope.data?.toDomain())
                }
            }
        }
    }

    private companion object {
        const val MAX_HTTP_CODE: Int = 500
    }
}

private fun RegisterSessionDto.toDomain(): RegisterSession = RegisterSession(
    id = id,
    employeeName = employeeName,
    openedAt = openedAt,
    openingCashCents = toCents(openingCash),
)

private fun SessionReportDto.toDomain(): SessionReport = SessionReport(
    employeeName = employeeName,
    openedAt = openedAt,
    closedAt = closedAt,
    openingCashCents = toCents(openingCash),
    totalTransactions = summary.totalTransactions,
    totalSalesCents = toCents(summary.totalSales),
    cashTotalCents = toCents(summary.cashTotal),
    creditTotalCents = toCents(summary.creditTotal),
    refundTotalCents = toCents(summary.refundTotal),
    netSalesCents = toCents(summary.netSales),
    expectedCashCents = toCents(summary.expectedCash),
)

private fun CashCount.toDto(): ClosingBreakdownDto = ClosingBreakdownDto(
    bills = mapOf(
        "bills_100" to bills100,
        "bills_50" to bills50,
        "bills_20" to bills20,
        "bills_10" to bills10,
        "bills_5" to bills5,
        "bills_1" to bills1,
    ),
    coins = mapOf(
        "coins_100" to dollarCoins,
        "coins_50" to halfDollars,
        "coins_25" to quarters,
        "coins_10" to dimes,
        "coins_5" to nickels,
        "coins_1" to pennies,
    ),
    rolls = mapOf(
        "rolls_100" to dollarRolls,
        "rolls_50" to halfDollarRolls,
        "rolls_25" to quarterRolls,
        "rolls_10" to dimeRolls,
        "rolls_5" to nickelRolls,
        "rolls_1" to pennyRolls,
    ),
    straps = mapOf(
        "straps_20" to straps20,
        "straps_10" to straps10,
        "straps_5" to straps5,
        "straps_1" to straps1,
    ),
    totals = ClosingBreakdownTotalsDto(
        billsTotal = toDollars(billsCents),
        coinsTotal = toDollars(coinsCents),
        rollsTotal = toDollars(rollsCents),
        strapsTotal = toDollars(strapsCents),
    ),
)

/** Mirrors `CatalogRepositoryImpl.toCents` — half-up rounding via a string-seeded BigDecimal. */
private fun toCents(dollars: Double): Long =
    BigDecimal(dollars.toString())
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .toLong()

private fun toDollars(cents: Long): Double =
    BigDecimal(cents).movePointLeft(2).toDouble()
