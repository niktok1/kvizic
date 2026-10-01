import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// The app's version, kvizic.app.version in gradle.properties, every platform's, and the build number made
// from it, which every request names.
apply(from = rootProject.file("gradle/kvizic-version.gradle.kts"))
val appVersion = extra["kvizicAppVersion"] as String
val buildNumber = extra["kvizicBuildNumber"] as Int

// The PostHog project this build sends analytics to, from kvizic.posthog.key and kvizic.posthog.host, as a
// Gradle property or in local.properties: none is analytics off.
apply(from = rootProject.file("gradle/kvizic-analytics.gradle.kts"))
val posthogKey = extra["kvizicPosthogKey"] as String
val posthogHost = extra["kvizicPosthogHost"] as String

// Play Games' ids, from kvizic.playgames.* as a Gradle property or in local.properties: none is Play Games
// off on the build.
apply(from = rootProject.file("gradle/kvizic-android-services.gradle.kts"))
val playGamesAppId = extra["kvizicPlayGamesAppId"] as String
val playGamesServerClientId = extra["kvizicPlayGamesServerClientId"] as String

// The Play upload key: each of the four settings from local.properties, which git ignores, or else from the
// environment, and never committed. Only prod goes to Google Play, so only prod's release build is signed with
// it, once all four are set; with fewer, with the debug key, so it still installs on a phone for testing, and
// signing prod's bundle, which is what Play takes, fails before anything runs. Dev's and local's release
// builds always take the debug key (the flavors, below).
val localProperties =
    Properties().apply {
        providers
            .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
            .asText
            .orNull
            ?.let { text -> load(StringReader(text)) }
    }

fun uploadSetting(
    property: String,
    variable: String,
): String? =
    (localProperties.getProperty(property) ?: providers.environmentVariable(variable).orNull)
        ?.takeIf { it.isNotBlank() }

val uploadStoreFile = uploadSetting("kvizic.upload.storeFile", "KVIZIC_UPLOAD_STORE_FILE")
val uploadStorePassword = uploadSetting("kvizic.upload.storePassword", "KVIZIC_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = uploadSetting("kvizic.upload.keyAlias", "KVIZIC_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = uploadSetting("kvizic.upload.keyPassword", "KVIZIC_UPLOAD_KEY_PASSWORD")
val uploadKeyMissing =
    mapOf(
        "kvizic.upload.storeFile" to uploadStoreFile,
        "kvizic.upload.storePassword" to uploadStorePassword,
        "kvizic.upload.keyAlias" to uploadKeyAlias,
        "kvizic.upload.keyPassword" to uploadKeyPassword,
    ).filterValues { it == null }.keys
val prodReleaseSigning = if (uploadKeyMissing.isEmpty()) "upload" else "debug"

if (uploadKeyMissing.isNotEmpty()) {
    val missing = uploadKeyMissing.joinToString()
    val signsPlayBundle = "$path:signProdReleaseBundle"
    // Asked once the task graph is known, so the bundle fails before any task runs. signProdReleaseBundle
    // writes prod's .aab, and bundleProdRelease and bundleRelease run it, so it is what is looked for: run by
    // itself, it writes the same file. Dev's and local's bundles are the debug key's whatever is set, and never
    // go to Play.
    gradle.taskGraph.whenReady {
        if (hasTask(signsPlayBundle)) {
            throw GradleException(
                "The prod bundle is what Google Play takes, which must be signed with the Play upload key, and " +
                    "it is not configured: set $missing in local.properties, or the KVIZIC_UPLOAD_* variables.",
            )
        }
    }
    // Said by prod's release APK's packaging as it signs one: an APK up to date signs nothing.
    val warning = "Signed with the debug key: the Play upload key is not configured ($missing)."
    tasks.named { it == "packageProdRelease" }.configureEach {
        doFirst { logger.warn("$name: $warning") }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":app:shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    testImplementation(libs.kotlin.testJunit)
    // The storage's file name and the default skin's page, which the resources' platform copies are held to.
    testImplementation(project(":core:network"))
    testImplementation(project(":app:designsystem"))
}

// The resource tests read the manifest and the resources from disk, so they are inputs of every unit test
// run, which would otherwise be up to date after a resource changed.
tasks.withType<Test>().configureEach {
    inputs.file("src/main/AndroidManifest.xml").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/res").withPathSensitivity(PathSensitivity.RELATIVE)
}

android {
    namespace = "io.ntole.kvizic"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "io.ntole.kvizic"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = buildNumber
        versionName = appVersion
        // One project for every flavor: each event names its environment.
        buildConfigField("String", "POSTHOG_KEY", "\"$posthogKey\"")
        buildConfigField("String", "POSTHOG_HOST", "\"$posthogHost\"")
        // One Play Games project for every flavor: its id is the manifest's APP_ID, a string resource as Play
        // Games reads it, empty on a build without it.
        buildConfigField("String", "PLAY_GAMES_APP_ID", "\"$playGamesAppId\"")
        buildConfigField("String", "PLAY_GAMES_SERVER_CLIENT_ID", "\"$playGamesServerClientId\"")
        resValue("string", "game_services_project_id", playGamesAppId)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        if (uploadKeyMissing.isEmpty()) {
            create("upload") {
                // A path relative to the repository's root, where local.properties is, or absolute.
                storeFile = rootProject.file(uploadStoreFile.orEmpty())
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }
    buildTypes {
        release {
            // Signed per flavor (productFlavors, below), since a build type's signing config would win over
            // every flavor's.
            // R8 shrinks, optimizes and renames the code, and drops the resources nothing uses. Its mapping,
            // which turns a crash's renamed stack trace back into these names, lands in
            // build/outputs/mapping/<variant>/mapping.txt, and a bundle carries it to Play itself.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig carries each flavor's environment, the analytics project and Play Games' ids to
        // KvizicApplication, and resValue its label and the Play Games project's id.
        buildConfig = true
        resValues = true
    }

    // One flavor per server environment, each under an id and a launcher label of its own, so all three
    // install side by side. Only local may send plain http: an emulator or a phone reaches a server on the
    // developer's machine at http://localhost:8080 through `adb reverse tcp:8080 tcp:8080`, and the deployed
    // ones are https only.
    flavorDimensions += "environment"
    productFlavors {
        create("local") {
            applicationIdSuffix = ".local"
            resValue("string", "app_name", "Kvizic Local")
            manifestPlaceholders["usesCleartextTraffic"] = true
        }
        create("dev") {
            // Android Studio's default variant: the deployed dev server needs nothing on the machine.
            isDefault = true
            applicationIdSuffix = ".dev"
            resValue("string", "app_name", "Kvizic Dev")
            manifestPlaceholders["usesCleartextTraffic"] = false
        }
        create("prod") {
            // The game's name in Serbian Cyrillic, whatever the device's language; the Play listing's name
            // is set apart, in the Play Console.
            resValue("string", "app_name", "Квизић")
            manifestPlaceholders["usesCleartextTraffic"] = false
        }
        configureEach {
            dimension = "environment"
            // The flavor's name is its environment's, as KvizicEnvironment.parse reads it.
            buildConfigField("String", "KVIZIC_ENV", "\"$name\"")
            // Only prod's release build takes the upload key (above). Dev's and local's keep the debug key, as
            // their debug builds do (the debug build type's own, which wins over this), so a release build
            // installs over a debug one and back, keeping its data: Android refuses an update signed with
            // another key.
            signingConfig = signingConfigs.getByName(if (name == "prod") prodReleaseSigning else "debug")
        }
    }
}
