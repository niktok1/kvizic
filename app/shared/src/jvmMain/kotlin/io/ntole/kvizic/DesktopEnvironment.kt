package io.ntole.kvizic

import io.ntole.kvizic.analytics.AnalyticsSettings

/*
 * What the desktop client reads of its process when it starts. Here rather than in `:app:desktopApp`,
 * which only hands it on, since shared code can read it. Each reads [variables] or [properties], the
 * process's own unless a test gives others.
 */

/**
 * The variable that names the desktop client's server environment: local, dev or prod.
 * `KVIZIC_ENV=dev ./gradlew :app:desktopApp:run` passes it on to the app.
 */
internal const val ENVIRONMENT_VARIABLE: String = "KVIZIC_ENV"

/**
 * The name [ENVIRONMENT_VARIABLE] gives, as it is, or `null` when it is unset, which
 * [io.ntole.kvizic.di.initKoin] reads as local; a name it does not know stops the app there.
 */
fun desktopEnvironmentName(variables: Map<String, String> = System.getenv()): String? = variables[ENVIRONMENT_VARIABLE]

/**
 * The variable that names the desktop client's profile: each one is a player of its own, kept apart on
 * this machine, so `KVIZIC_PROFILE=ana` and `KVIZIC_PROFILE=boris` in two windows play one lobby together.
 */
internal const val PROFILE_VARIABLE: String = "KVIZIC_PROFILE"

/** The name [PROFILE_VARIABLE] gives, as it is, or `null` when it is unset: the default player. */
fun desktopProfileName(variables: Map<String, String> = System.getenv()): String? = variables[PROFILE_VARIABLE]

/** The variable that names the PostHog project's key the desktop client sends analytics to. */
internal const val POSTHOG_KEY_VARIABLE: String = "KVIZIC_POSTHOG_KEY"

/** The variable that names that project's host, PostHog's EU cloud when unset. */
internal const val POSTHOG_HOST_VARIABLE: String = "KVIZIC_POSTHOG_HOST"

/** The system property the desktop build names the app's version in (`:app:desktopApp`'s `jvmArgs`). */
internal const val APP_VERSION_PROPERTY: String = "kvizic.app.version"

/**
 * The analytics the desktop client sends: [POSTHOG_KEY_VARIABLE], none being analytics off, and
 * [POSTHOG_HOST_VARIABLE], as `KVIZIC_POSTHOG_KEY=phc_... ./gradlew :app:desktopApp:run` names them; and
 * the app's version, from [APP_VERSION_PROPERTY].
 */
fun desktopAnalyticsSettings(
    variables: Map<String, String> = System.getenv(),
    properties: Map<String, String> = systemProperties(),
): AnalyticsSettings =
    AnalyticsSettings(
        key = variables[POSTHOG_KEY_VARIABLE],
        host = variables[POSTHOG_HOST_VARIABLE],
        appVersion = properties[APP_VERSION_PROPERTY].orEmpty(),
    )

/** The system property the desktop build names its build number in (`:app:desktopApp`'s `jvmArgs`). */
internal const val BUILD_NUMBER_PROPERTY: String = "kvizic.app.build"

/**
 * The build number [BUILD_NUMBER_PROPERTY] names, or null when it names no whole number: a desktop app
 * started without it, from an IDE say, whose requests then name no build.
 */
fun desktopBuildNumber(properties: Map<String, String> = systemProperties()): Int? =
    properties[BUILD_NUMBER_PROPERTY]?.trim()?.toIntOrNull()

/**
 * The window's title: the game's name, and the profile played as when there is one, so windows side by
 * side say which player each is.
 */
fun desktopWindowTitle(profile: String?): String =
    profile?.trim()?.takeIf { it.isNotEmpty() }?.let { "$WINDOW_TITLE · $it" } ?: WINDOW_TITLE

/** The game's name as the window's title bar shows it, in the Latin every desktop's font has. */
internal const val WINDOW_TITLE: String = "Kvizić"

private fun systemProperties(): Map<String, String> =
    System.getProperties().stringPropertyNames().associateWith(System::getProperty)
