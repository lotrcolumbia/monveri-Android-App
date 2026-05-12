plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt)
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    autoCorrect = false
}

dependencies {
    detektPlugins(libs.detekt.formatting)
}

tasks.register("detektAll") {
    group = "verification"
    description = "Runs detekt on every leaf subproject that applies the plugin."
    // `include(":core:design")` etc. implicitly create empty parent projects (`:core`, `:feature`)
    // that don't apply the detekt plugin — filter to leaf modules with a real buildFile.
    dependsOn(
        subprojects
            .filter { it.buildFile.exists() }
            .map { "${it.path}:detekt" }
    )
}
