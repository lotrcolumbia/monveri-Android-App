pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// Resolves JDK 17 toolchain requests against the Foojay disco API when a matching local install
// isn't present. Lets fresh checkouts and ephemeral CI runners build without manual SDKMAN setup.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MonveriRegister"

include(":app")
include(":core:model")
include(":core:design")
include(":core:network")
include(":core:data")
include(":core:pricing")
include(":core:payments")
include(":feature:auth")
include(":feature:catalog")
include(":feature:cart")
include(":feature:settings")
