import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    android {
        namespace = "io.ntole.kvizic.design"
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
        // The bundled fonts are compose resources, which Android packages as assets.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Foundation and nothing of Material's: a Material component draws itself its own way and
            // resists a skin that changes shapes, depth and renderers, not only colours.
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            implementation(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            // This machine's Skia, so a test can draw off screen (ImageComposeScene) and read the fonts.
            implementation(compose.desktop.currentOs)
            // The wire's limits, so a test draws the longest texts the rules allow and never a copy of them.
            implementation(project(":core"))
        }
    }
}

compose.resources {
    // Internal: a screen reads the fonts through the skin, never through the generated accessors.
    publicResClass = false
    packageOfResClass = "io.ntole.kvizic.design.resources"
}

// The draw tests render every skin a frame at a time in software, bound by the processor: their classes are
// shared out among test JVMs, one a core, up to four (CI's runner has four). The tests of a class share one.
tasks.named<Test>("jvmTest") {
    maxParallelForks = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
}
