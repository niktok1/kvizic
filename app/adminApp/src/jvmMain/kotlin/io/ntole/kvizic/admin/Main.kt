package io.ntole.kvizic.admin

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.ntole.kvizic.admin.di.initAdminKoin

/** The moderation app's window: `KVIZIC_ENV` names the server, local when unset (`KVIZIC_ENV=dev`); `KVIZIC_ADMIN_TOKEN`, set by the run task from
 * local.properties, unlocks it. */
fun main() {
    val environment = initAdminKoin(System.getenv("KVIZIC_ENV"))
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = windowTitleOf(environment),
            state = rememberWindowState(width = WIDTH.dp, height = HEIGHT.dp),
        ) {
            AdminApp(token = System.getenv("KVIZIC_ADMIN_TOKEN"))
        }
    }
}

private const val WIDTH = 1280
private const val HEIGHT = 860
