package io.ntole.kvizic.admin

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import io.ntole.kvizic.admin.di.initAdminKoin

/**
 * The moderation app's page, for the environment the build was made for ([KVIZIC_ENV], `-Pkvizic.env`). The
 * server must list the page's origin in its `ALLOWED_WEB_ORIGINS`, or every request fails CORS.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initAdminKoin(KVIZIC_ENV)
    ComposeViewport { AdminApp() }
}
