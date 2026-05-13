package co.monveri.register.feature.catalog

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Hosts the 3-frame stability debounce for the scanner. Lives at the ViewModel layer (not in
 * Composables) so process recreations don't reset the counter mid-scan.
 *
 * `onScanResult` is invoked at most once per [reset]; the caller is expected to navigate away
 * (or call [reset]) before scanning the next code.
 */
@HiltViewModel
class BarcodeScannerViewModel @Inject constructor() : ViewModel() {

    private var lastCode: String? = null
    private var streak: Int = 0
    private var hasFired: Boolean = false

    /**
     * Called from the ML Kit analyzer for each frame that yields a decoded barcode. The same
     * code must appear in [STABLE_FRAME_COUNT] consecutive frames before [onStable] fires.
     */
    fun onBarcodeDetected(code: String, onStable: (String) -> Unit) {
        if (hasFired) return
        if (code == lastCode) {
            streak += 1
            if (streak >= STABLE_FRAME_COUNT) {
                hasFired = true
                onStable(code)
            }
        } else {
            lastCode = code
            streak = 1
        }
    }

    fun reset() {
        lastCode = null
        streak = 0
        hasFired = false
    }

    private companion object {
        const val STABLE_FRAME_COUNT: Int = 3
    }
}
