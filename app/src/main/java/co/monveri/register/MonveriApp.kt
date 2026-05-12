package co.monveri.register

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Hilt instruments this class to generate the component tree.
 * No imperative work in Phase 1 — Hilt sets up SecurePrefs lazily on first inject.
 */
@HiltAndroidApp
class MonveriApp : Application()
