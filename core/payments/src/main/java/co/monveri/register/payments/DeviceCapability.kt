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
        // `ReaderSupportResult` carries the *reason* a device fails (a Throwable set by the SDK —
        // e.g. an ATTESTATION_FAILURE for a non-GMS-certified build, an insecure-environment
        // check, or a keystore capability gap) — surfacing `.message` turns "Stripe doesn't
        // support this device" from a dead end into something the cashier (or Stripe support) can
        // actually act on. Any SDK exception during the call itself is still treated as
        // unsupported rather than crashing the diagnostics screen.
        //
        // `isSimulated = BuildConfig.DEBUG`: Stripe's production Tap to Pay reader flatly refuses
        // to run inside any debuggable APK ("Debuggable applications are not supported... use a
        // simulated version of the reader"), independent of every device-level signal above — a
        // release build always asks for the real reader. See [TapToPayService] for the matching
        // connect-time flag.
        val result = runCatching {
            terminalManager.ensureInitialized()
            Terminal.getInstance().supportsReadersOfType(
                deviceType = DeviceType.TAP_TO_PAY_DEVICE,
                discoveryConfiguration = DiscoveryConfiguration.TapToPayDiscoveryConfiguration(
                    isSimulated = BuildConfig.DEBUG,
                ),
            )
        }.getOrNull()
        return local.copy(
            stripeSupported = result?.isSupported ?: false,
            unsupportedReason = result?.error?.message,
            isSimulated = BuildConfig.DEBUG,
        )
    }

    private companion object {
        // Android 13 — Stripe's current documented floor for Tap to Pay on Android
        // (docs.stripe.com/terminal/payments/setup-reader/tap-to-pay?platform=android), which the
        // docs themselves warn "can change due to updated compliance requirements". This is a
        // per-feature floor enforced in code rather than the manifest so a single APK still
        // installs on the API 29 minSdk (Tap to Pay simply stays hidden below API 33).
        const val MIN_TAP_TO_PAY_SDK: Int = Build.VERSION_CODES.TIRAMISU
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
    /** The SDK's own explanation when [stripeSupported] is `false` — null otherwise. */
    val unsupportedReason: String? = null,
    /**
     * True whenever [stripeSupported] was checked against Stripe's *simulated* reader rather than
     * the real one — always true in debug builds (see [DeviceCapability.fullReadiness]). A green
     * "Stripe supports this device" in this state validates the app's own flow, not real hardware
     * compatibility or a live card tap; the UI must say so rather than implying production
     * readiness.
     */
    val isSimulated: Boolean = false,
) {
    /** True only when every signal is green — fail-closed on the unknown ([stripeSupported] null). */
    val isReady: Boolean
        get() = osVersionOk && hasNfcHardware && nfcEnabled && stripeSupported == true
}
