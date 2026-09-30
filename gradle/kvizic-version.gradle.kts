/*
 * The app's version, named once: `kvizic.app.version` in the repository's gradle.properties, three whole
 * numbers, MAJOR.MINOR.PATCH, the one form a desktop MSI package takes. Every analytics event carries it
 * as `$app_version`, so every platform's build must say the same, or a breakdown by version splits one
 * release in two.
 *
 * The build number is made from it, the same on every platform: MAJOR * 10000 + MINOR * 100 + PATCH, so
 * 1.2.3 is 10203, and MINOR and PATCH must each stay below 100 for the numbers to keep growing with the
 * version. It is Android's `versionCode`, and every request the game sends names it in
 * `X-Client-Version`, which the server's minimum for the platform is compared with. Play refuses a
 * `versionCode` it has seen, so every upload bumps PATCH (or MINOR) first.
 *
 * Applied by each module that builds an app, which reads `extra["kvizicAppVersion"]` and
 * `extra["kvizicBuildNumber"]`: `:app:androidApp` for its `versionName` and `versionCode`,
 * `:app:desktopApp` for its `packageVersion` and the `kvizic.app.version` and `kvizic.app.build`
 * properties it runs with, and `:app:webApp` for the `APP_VERSION` and `BUILD_NUMBER` constants that
 * `generateKvizicAnalytics` writes (gradle/kvizic-analytics.gradle.kts).
 *
 * Xcode reads the iOS app's own, `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` (its
 * `CFBundleVersion`), from app/iosApp/Configuration/Config.xcconfig, which Gradle cannot write for it: so
 * every build fails here until they say the same as this.
 */
val appVersion = providers.gradleProperty("kvizic.app.version").orNull.orEmpty().trim()
require(Regex("""\d+\.\d+\.\d+""").matches(appVersion)) {
    "kvizic.app.version must be MAJOR.MINOR.PATCH, three whole numbers: \"$appVersion\""
}

val (major, minor, patch) = appVersion.split('.').map(String::toInt)
require(minor < 100 && patch < 100) {
    "kvizic.app.version $appVersion: MINOR and PATCH must each be below 100, or the build number " +
        "(MAJOR * 10000 + MINOR * 100 + PATCH) would not grow with the version"
}
val buildNumber = major * 10_000 + minor * 100 + patch
require(buildNumber >= 1) { "kvizic.app.version $appVersion makes build number 0; Android needs at least 1" }

val iosConfig = rootProject.layout.projectDirectory.file("app/iosApp/Configuration/Config.xcconfig")
providers.fileContents(iosConfig).asText.orNull?.let { text ->
    fun setting(name: String): String? =
        text
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.substringBefore("=").trim() == name }
            ?.substringAfter("=")
            ?.trim()

    val marketingVersion = setting("MARKETING_VERSION")
    require(marketingVersion == appVersion) {
        "MARKETING_VERSION in app/iosApp/Configuration/Config.xcconfig is $marketingVersion, but " +
            "kvizic.app.version is $appVersion: change the two together"
    }
    val projectVersion = setting("CURRENT_PROJECT_VERSION")
    require(projectVersion == buildNumber.toString()) {
        "CURRENT_PROJECT_VERSION in app/iosApp/Configuration/Config.xcconfig is $projectVersion, but " +
            "kvizic.app.version $appVersion makes build number $buildNumber: set it to $buildNumber"
    }
}

extra["kvizicAppVersion"] = appVersion
extra["kvizicBuildNumber"] = buildNumber
