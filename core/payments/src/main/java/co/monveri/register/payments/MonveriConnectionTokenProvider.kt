package co.monveri.register.payments

import co.monveri.register.network.MonveriApi
import com.stripe.stripeterminal.external.callable.ConnectionTokenCallback
import com.stripe.stripeterminal.external.callable.ConnectionTokenProvider
import com.stripe.stripeterminal.external.models.ConnectionTokenException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the Stripe Terminal SDK's callback-based [ConnectionTokenProvider] to our suspend-based
 * Retrofit API. The SDK calls [fetchConnectionToken] whenever it needs to (re-)auth a reader; we
 * hit `POST /payments/connection-token.php` and hand the secret back.
 *
 * Failures are surfaced as [ConnectionTokenException] so the SDK can retry / propagate to the
 * connect/payment callbacks. The error message is preserved for the cashier-facing error sheet.
 */
@Singleton
class MonveriConnectionTokenProvider @Inject constructor(
    private val api: MonveriApi,
) : ConnectionTokenProvider {

    // Scope is process-lifetime; SupervisorJob keeps a single failed token fetch from cancelling
    // the scope so the next reader-event-driven fetch can still run.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun fetchConnectionToken(callback: ConnectionTokenCallback) {
        scope.launch {
            try {
                val envelope = api.stripeConnectionToken()
                val payload = envelope.data
                val secret = payload?.secret?.trim().orEmpty()
                when {
                    !envelope.success || payload == null -> callback.onFailure(
                        ConnectionTokenException(
                            envelope.message ?: "Connection token request failed",
                        ),
                    )
                    // Defend against a backend regression that returns `success=true` with an
                    // empty `secret` — the SDK would accept it then fail mid-auth with a less
                    // diagnostic error. Failing loudly here keeps the cashier-facing message clean.
                    secret.isBlank() -> callback.onFailure(
                        ConnectionTokenException("Connection token is blank"),
                    )
                    else -> callback.onSuccess(secret)
                }
            } catch (e: Exception) {
                // Wrapping non-Stripe exceptions keeps a single failure surface the SDK understands.
                callback.onFailure(
                    ConnectionTokenException(e.message ?: "Network error", e),
                )
            }
        }
    }
}
