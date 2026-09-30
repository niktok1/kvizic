rootProject.name = "kvizic"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// The wire contract and the realtime protocol, shared by :server and the client layers.
include(":core")

include(":server")

/**
 * The server image builds only `:server` and `:core`. The Android Gradle plugin needs an SDK at
 * configuration time, so merely declaring the app modules would fail a Docker build that has none.
 */
val serverOnly =
    providers.environmentVariable("KVIZIC_SERVER_ONLY").isPresent ||
        providers.gradleProperty("kvizic.serverOnly").isPresent

if (!serverOnly) {
    // Client layers. Dependencies point inward: :core:domain depends on nothing.
    include(":core:domain")
    include(":core:network")
    include(":core:data")

    // The skin engine, then the game's screens, then each platform's entry point.
    include(":app:designsystem")
    include(":app:shared")
    include(":app:androidApp")
    include(":app:desktopApp")
    include(":app:webApp")

    // The moderation app, desktop and browser, on the client layers alone.
    include(":app:adminApp")
}
