import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The moderation app: a desktop window, a browser page and, for the owner's own phone, an Android library
// (:app:adminAndroidApp is its entry point); no iOS build. The page's environment comes from
// -Pkvizic.env, written into io.ntole.kvizic.admin.KVIZIC_ENV.
extra["kvizicEnvPackage"] = "io.ntole.kvizic.admin"
apply(from = rootProject.file("gradle/kvizic-env.gradle.kts"))

kotlin {
    jvm()

    android {
        namespace = "io.ntole.kvizic.admin"
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
    }

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
            implementation(project(":core:domain"))
            implementation(project(":core:data"))
            implementation(project(":core:network"))
            implementation(project(":app:designsystem"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.androidx.lifecycle.viewmodelCompose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
        webMain.configure {
            kotlin.srcDir(tasks.named("generateKvizicEnv"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "io.ntole.kvizic.admin.MainKt"
    }
}

// The desktop run unlocks by itself with kvizic.adminToken from local.properties, which git ignores, handed
// over as an environment variable (not a system property, which `ps` shows). The web page never gets it: a
// build would put it in the served bundle.
val adminToken =
    Properties()
        .apply {
            providers
                .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
                .asText
                .orNull
                ?.let { text -> load(StringReader(text)) }
        }.getProperty("kvizic.adminToken")
tasks.withType<JavaExec>().matching { it.name == "run" }.configureEach {
    adminToken?.takeIf { it.isNotBlank() }?.let { environment("KVIZIC_ADMIN_TOKEN", it) }
}
