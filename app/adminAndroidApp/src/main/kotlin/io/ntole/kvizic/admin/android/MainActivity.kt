package io.ntole.kvizic.admin.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.ntole.kvizic.admin.AdminApp

/** Binds the moderation app to the activity; the debug build hands it the token from local.properties. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { AdminApp(token = BuildConfig.ADMIN_TOKEN) }
    }
}
