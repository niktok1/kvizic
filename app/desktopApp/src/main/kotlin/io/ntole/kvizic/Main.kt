package io.ntole.kvizic

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.ntole.kvizic.di.initKoin
import kotlin.system.exitProcess

/**
 * The desktop client, the one to build the game with: `KVIZIC_ENV` names the server environment
 * ([desktopEnvironmentName]), `KVIZIC_PROFILE` the player ([desktopProfileName]), each profile a player of
 * its own on this machine, so `KVIZIC_PROFILE=ana` and `KVIZIC_PROFILE=boris` in two windows play one lobby
 * together; `KVIZIC_POSTHOG_KEY` and `KVIZIC_POSTHOG_HOST` the analytics project ([desktopAnalyticsSettings]),
 * and the `kvizic.app.build` system property the build number every request names ([desktopBuildNumber]).
 *
 * Once the window is closed, and before the process exits, the app's last analytics are sent
 * ([endDesktopApp]): the JVM would otherwise end with them waiting, or cut short.
 */
fun main() {
    initKoin(
        environmentName = desktopEnvironmentName(),
        analytics = desktopAnalyticsSettings(),
        build = desktopBuildNumber(),
    )

    application(exitProcessOnExit = false) {
        Window(
            onCloseRequest = ::exitApplication,
            title = desktopWindowTitle(desktopProfileName()),
        ) {
            App()
        }
    }
    endDesktopApp()
    exitProcess(0)
}
