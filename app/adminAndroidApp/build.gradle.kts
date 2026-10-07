import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// The moderation app on the owner's own phone, installed from the Mac (`installDebug`) and never published:
// kvizic.adminToken from local.properties, which git ignores, is compiled into the debug build so it unlocks
// by itself, and so the APK must never leave the phone. The release build carries none and asks for the token.
// kvizic.adminEnv (local, dev or prod) names the server, dev when unset.
val localProperties =
    Properties().apply {
        providers
            .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
            .asText
            .orNull
            ?.let { text -> load(StringReader(text)) }
    }
val adminToken = localProperties.getProperty("kvizic.adminToken").orEmpty().trim()
val adminEnv =
    localProperties
        .getProperty("kvizic.adminEnv")
        .orEmpty()
        .trim()
        .ifEmpty { "dev" }

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":app:adminApp"))
    // initAdminKoin answers a KvizicEnvironment, which the entry point compiles against.
    implementation(project(":core:network"))
    implementation(libs.androidx.activity.compose)
}

android {
    namespace = "io.ntole.kvizic.admin.android"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "io.ntole.kvizic.admin"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "KVIZIC_ENV", "\"$adminEnv\"")
    }
    buildTypes {
        debug {
            buildConfigField("String", "ADMIN_TOKEN", "\"$adminToken\"")
        }
        release {
            buildConfigField("String", "ADMIN_TOKEN", "\"\"")
            isMinifyEnabled = false
            // Debug-signed so it installs on the phone; it is not for any store.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}
