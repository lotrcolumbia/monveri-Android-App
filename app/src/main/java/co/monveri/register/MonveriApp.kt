package co.monveri.register

import android.app.Application
import co.monveri.register.payments.TerminalManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Hilt instruments this class to generate the component tree.
 *
 * Phase 4: warm up the Stripe Terminal SDK on first launch so reader auto-reconnect can run
 * without waiting for the cashier to open the catalog. [TerminalManager.ensureInitialized] is
 * idempotent — if Hilt re-creates the Application (e.g. process death) the second call is a
 * cheap no-op.
 */
@HiltAndroidApp
class MonveriApp : Application() {

    @Inject lateinit var terminalManager: TerminalManager

    override fun onCreate() {
        super.onCreate()
        terminalManager.ensureInitialized()
    }
}
