package io.ntole.kvizic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Binds the shared UI to the Android activity lifecycle, and nothing else: DI is started in
 * [KvizicApplication], and every screen lives in `:app:shared`.
 *
 * There is deliberately no `@Preview` of [App] here: it resolves its ViewModels from Koin, which the
 * preview renderer never starts, so the preview would only ever throw.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}
