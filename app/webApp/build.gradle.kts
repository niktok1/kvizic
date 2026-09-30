import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The server environment this web build targets, from -Pkvizic.env: generateKvizicEnv writes it into
// io.ntole.kvizic.KVIZIC_ENV, which the entry point hands to initKoin.
extra["kvizicEnvPackage"] = "io.ntole.kvizic"
apply(from = rootProject.file("gradle/kvizic-env.gradle.kts"))

// The PostHog project this web build sends analytics to, from kvizic.posthog.key and kvizic.posthog.host, as
// a Gradle property or in local.properties: generateKvizicAnalytics writes them, and the app's version,
// kvizic.app.version in gradle.properties, with its build number, into io.ntole.kvizic's POSTHOG_KEY,
// POSTHOG_HOST, APP_VERSION and BUILD_NUMBER. None is off.
apply(from = rootProject.file("gradle/kvizic-version.gradle.kts"))
extra["kvizicAnalyticsPackage"] = "io.ntole.kvizic"
apply(from = rootProject.file("gradle/kvizic-analytics.gradle.kts"))

kotlin {
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":app:shared"))

            implementation(libs.compose.ui)
        }
        webMain.configure {
            kotlin.srcDir(tasks.named("generateKvizicEnv"))
            kotlin.srcDir(tasks.named("generateKvizicAnalytics"))
        }
    }
}
