import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    // Declared once here so every subproject loads the plugins from one classloader.
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.ktlint)
}

// ktlint on every module; CI fails on a violation.
val ktlintCliVersion =
    libs.versions.ktlint.cli
        .get()

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<KtlintExtension> {
        version.set(ktlintCliVersion)
        filter {
            exclude { element -> element.file.path.contains("/build/") }
        }
    }
}
