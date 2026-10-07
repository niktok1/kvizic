package io.ntole.kvizic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.ntole.kvizic.share.InviteLink

/**
 * Binds the shared UI to the Android activity lifecycle, and nothing else: DI is started in
 * [KvizicApplication], and every screen lives in `:app:shared`. A room's link it is opened at, at launch or
 * while it runs (one activity, `singleTask`), goes to [InviteLink].
 *
 * There is deliberately no `@Preview` of [App] here: it resolves its ViewModels from Koin, which the
 * preview renderer never starts, so the preview would only ever throw.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // An activity made anew, on a rotation say, keeps the intent it was opened at: that link was taken.
        if (savedInstanceState == null) opened(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        opened(intent)
    }

    private fun opened(intent: Intent?) {
        // Opened again from the recent apps, the intent is the old link's, taken long ago.
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        if (intent.action == Intent.ACTION_VIEW) intent.dataString?.let(InviteLink::opened)
    }
}
