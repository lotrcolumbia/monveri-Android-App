package co.monveri.register.payments

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import com.stripe.stripeterminal.Terminal
import com.stripe.stripeterminal.external.models.DeviceType
import com.stripe.stripeterminal.external.models.DiscoveryConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime gate for the Tap to Pay on Android path. Tap to Pay only works on a narrow slice of
 * devices (Android 11+, NFC + secure element, Play Services, Play Protect on, not rooted), so the
 * UI must never offer it unless every signal checks out — a half-supported device fails mid-tap
 * with an opaque SDK error, which reads to the cashier as "the app is broken".
 *
 * Two layers of checking:
 *  1. Cheap, local Android signals (OS version, NFC hardware, NFC enabled) — no SDK needed, used
 *     to hide the entry point before the Terminal SDK has even initialised.
 *  2. The authoritative Stripe check ([Terminal.supportsReadersOfType]) — Stripe maintains the
 *     real device allow-list server-side and surfaces it through the SDK. Only consulted once the
 *     local signals pass, since it needs an initialised Terminal.
 *
 * The result is a [TapToPayReadiness] bag rather than a bare Boolean so the diagnostics screen can
 * show the cashier *which* signal failed ("NFC is turned off" is actionable; "not supported" is not).
 */
@Singleton
class DeviceCapability @Inject constructor(
    @ApplicationContext private val context: Context,
    private val terminalManager: TerminalManager,
) {

    /** Cheap pre-flight — safe to call before the Terminal SDK is initialised. */
    fun localReadiness(): TapToPayReadiness {
        val osOk = Build.VERSION.SDK_INT >= MIN_TAP_TO_PAY_SDK
        val hasNfc = context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)
        // getDefaultAdapter returns null on devices with no NFC at all; isEnabled is the
        // user-toggleable runtime state (Settings → Connected devices → NFC).
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        val nfcEnabled = nfcAdapter?.isEnabled == true
        return TapToPayReadiness(
            osVersionOk = osOk,
            hasNfcHardware = hasNfc,
            nfcEnabled = nfcEnabled,
            // Unknown until the SDK is consulted — `null` renders as "checking…" in diagnostics.
            stripeSupported = null,
        )
    }

    /**
     * Full readiness including Stripe's authoritative device check. Initialises Terminal if it
     * hasn't been yet (idempotent). The Stripe call is wrapped defensively: any SDK exception is
     * treated as "not supported" rather than crashing the diagnostics screen — an unsupported
     * device is exactly the case where this can throw.
     */
    fun fullReadiness(): TapToPayReadiness {
        val local = localReadiness()
        // No point asking Stripe if the device can't physically do it — also avoids initialising
        // the SDK on a device that will never use it.
        if (!local.osVersionOk || !local.hasNfcHardware) {
            return local.copy(stripeSupported = false)
        }
        val supported = runCatching {
            terminalManager.ensureInitialized()
            Terminal.getInstance().supportsReadersOfType(
                deviceType = DeviceType.TAP_TO_PAY,
                discoveryConfiguration = DiscoveryConfiguration.TapToPayDiscoveryConfiguration(
                    isSimulated = false,
                ),
            ).isSupported
        }.getOrDefault(false)
        return local.copy(stripeSupported = supported)
    }

    private companion object {
        // Android 11. The plan raises the Tap to Pay floor to API 30 while the rest of the app
        // stays at the API 29 minSdk — this constant is that per-feature floor, enforced in code
        // rather than the manifest so a single APK still installs on API 29 devices (Tap to Pay
        // simply stays hidden there).
        const val MIN_TAP_TO_PAY_SDK: Int = Build.VERSION_CODES.R
    }
}

/**
 * Snapshot of every Tap to Pay readiness signal. [isReady] is the single gate the UI uses to
 * decide whether to offer Tap to Pay at all; the individual fields drive the diagnostics screen.
 *
 * [stripeSupported] is nullable on purpose: `null` means "not checked yet" (SDK not consulted),
 * distinct from `false` ("Stripe says this device can't").
 */
data class TapToPayReadiness(
    val osVersionOk: Boolean,
    val hasNfcHardware: Boolean,
    val nfcEnabled: Boolean,
    val stripeSupported: Boolean?,
) {
    /** True only when every signal is green — fail-closed on the unknown ([stripeSupported] null). */
    val isReady: Boolean
        get() = osVersionOk && hasNfcHardware && nfcEnabled && stripeSupported == true
}
