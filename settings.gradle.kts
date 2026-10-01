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
    // The client modules, each included once its directory exists: dependencies point inward, and
    // :core:domain depends on nothing.
    include(":core:domain")
    include(":core:network")
    include(":core:data")

    // The skin engine and its components, which every screen draws with.
    include(":app:designsystem")

    // The shared Compose UI, and each platform's entry point into it.
    include(":app:shared")
    include(":app:androidApp")
    include(":app:desktopApp")
    include(":app:webApp")

    // The moderation app, for whoever holds a server's admin token: desktop and web only.
    include(":app:adminApp")

    // Real clients against the real server, through a proxy that can slow or cut them.
    include(":e2e")
}
