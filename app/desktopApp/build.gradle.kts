import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

// The app's version, kvizic.app.version in gradle.properties, every platform's: its package's, and every
// analytics event's, which the app reads from the kvizic.app.version system property, on `run` as in a
// package; and the build number made from it, from kvizic.app.build, which every request names.
apply(from = rootProject.file("gradle/kvizic-version.gradle.kts"))
val appVersion = extra["kvizicAppVersion"] as String
val buildNumber = extra["kvizicBuildNumber"] as Int

compose.desktop {
    application {
        mainClass = "io.ntole.kvizic.MainKt"
        jvmArgs += listOf("-Dkvizic.app.version=$appVersion", "-Dkvizic.app.build=$buildNumber")

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "io.ntole.kvizic"
            packageVersion = appVersion
        }
    }
}
