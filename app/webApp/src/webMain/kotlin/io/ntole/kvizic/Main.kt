package io.ntole.kvizic

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.ntole.kvizic.analytics.AnalyticsSettings
import io.ntole.kvizic.di.initKoin

/**
 * [KVIZIC_ENV] is the environment the build was made for, `-Pkvizic.env`. The server it names must list
 * this page's origin in its `ALLOWED_WEB_ORIGINS`, or every request fails CORS. [POSTHOG_KEY] and
 * [POSTHOG_HOST] are the analytics project it sends to, from `kvizic.posthog.key` and `kvizic.posthog.host`,
 * none being off. [BUILD_NUMBER] is the build every request names, made from `kvizic.app.version`.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin(
        environmentName = KVIZIC_ENV,
        analytics = AnalyticsSettings(key = POSTHOG_KEY, host = POSTHOG_HOST, appVersion = APP_VERSION),
        build = BUILD_NUMBER,
    )

    ComposeViewport {
        App()
    }
}
