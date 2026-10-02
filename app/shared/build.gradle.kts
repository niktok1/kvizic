import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Duration

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "io.ntole.kvizic.app.shared"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
            // BackHandler, which binds Android's back to the back stack (SystemBack.android.kt).
            implementation(libs.androidx.activity.compose)
            // FileProvider, which hands a shared image to the app the player picks (ShareSheet.android.kt).
            implementation(libs.androidx.core.ktx)
            // api, not implementation: KvizicApplication calls androidContext() when starting DI, so this is
            // part of what the Android entry point compiles against.
            api(libs.koin.android)
            // Google Play Games Services v2, the sign-in with nothing to fill in: Android's own, with no
            // multiplatform equivalent, behind the PlayGames port of :core:domain.
            implementation(libs.play.services.gamesV2)
        }
        commonMain.dependencies {
            // The UI works in domain types only; :core:data is here purely to register DI bindings.
            api(project(":core:domain"))
            implementation(project(":core:data"))
            implementation(project(":core:network"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(project(":app:designsystem"))
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // api: initKoin() takes a KoinAppDeclaration, so entry points compile against Koin.
            api(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)
        }
        webMain.dependencies {
            // The page's own location, which the update screen's Reload loads again, and Web Share: the
            // browser API wrappers :core:network's web storage already reads through.
            implementation(libs.wrappers.browser)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jvmTest.dependencies {
            // This machine's Skia, so a test can draw a screen off screen (ImageComposeScene).
            implementation(compose.desktop.currentOs)
            // The wire's limits, so a test draws the longest texts the rules allow and never a copy of them.
            implementation(project(":core"))
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// The draw tests render every skin a frame at a time in software, bound by the processor: their classes are
// shared out among test JVMs, one a core, up to four (CI's runner has four). The tests of a class share one.
tasks.named<Test>("jvmTest") {
    maxParallelForks = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    // A draw test that hangs fails its task, with its reports, instead of holding CI's runner to the job's limit.
    timeout.set(Duration.ofMinutes(15))
    // On CI each test says when it starts and ends, so one that is slow or stuck shows in the log.
    if (providers.environmentVariable("CI").isPresent) {
        testLogging.events("started", "passed", "skipped", "failed")
    }
}
