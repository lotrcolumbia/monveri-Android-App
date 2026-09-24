import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

// Release signing — `key.properties` is git-ignored (see .gitignore); it and the keystore file
// under `keystore/` never get committed. Signing is skipped gracefully (falls back to no
// signingConfig, which Gradle then debug-signs) when the file is absent, so a fresh checkout
// without the keystore still builds a debug-installable APK — it just can't be uploaded to Play.
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
val hasReleaseSigning = keystorePropertiesFile.exists()
if (hasReleaseSigning) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    // Namespace matches the Kotlin/Java source package so manifest-relative class names
    // (`.MainActivity`, `.MonveriApp`) resolve to real classes. Distinct from `applicationId`,
    // which is the installed APK package ID.
    namespace = "co.monveri.register"
    compileSdk = 36

    defaultConfig {
        // Lowercase to match the Play Console app entry (`co.monveri.monveriregister`) — Play
        // locks the package name permanently at app creation and package IDs are case-sensitive,
        // so this has to match exactly. Deliberately not the same casing as the iOS bundle ID
        // (`co.monveri.MonveriRegister`); the two platforms' identifiers don't need to match.
        applicationId = "co.monveri.monveriregister"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        // Debug variants expose a Component Gallery debug route — gated on BuildConfig.DEBUG.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:design"))
    implementation(project(":core:network"))
    implementation(project(":core:data"))
    implementation(project(":core:pricing"))
    implementation(project(":core:payments"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:cart"))
    implementation(project(":feature:settings"))

    // Stripe Terminal types appear in the debug Test Harness ViewModel state — pin the SDK
    // here so the debug variant compiles even though :app's main code never calls Stripe.
    debugImplementation(libs.stripeterminal)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    // Component Gallery (debug variant) and other later screens consume extended icons.
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    detektPlugins(libs.detekt.formatting)
}
