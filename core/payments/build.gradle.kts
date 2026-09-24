plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

android {
    namespace = "co.monveri.register.payments"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    buildFeatures {
        // TerminalManager flips Stripe SDK log level on BuildConfig.DEBUG.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:network"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)

    // Stripe Terminal SDK — single combined artifact at v3.x includes the Bluetooth M2
    // transport. `api` because TerminalManager exposes Stripe types on its public surface
    // (TerminalListener supertype, ConnectionStatus/PaymentStatus/Reader StateFlows) — consumers
    // need them resolved.
    api(libs.stripeterminal)
    // Tap to Pay on Android (Phase 5) — separate add-on artifact carrying the on-device reader
    // engine (the TapToPay* API classes themselves live in the core artifact above).
    // `implementation`: used only inside TapToPayService/DeviceCapability, not on this module's
    // public surface. Must track the same version as the core stripeterminal artifact.
    implementation(libs.stripeterminal.taptopay)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Retrofit lives on the public surface here so MonveriConnectionTokenProvider can map
    // backend responses without leaking the network types into feature modules.
    implementation(libs.retrofit)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    detektPlugins(libs.detekt.formatting)
}
