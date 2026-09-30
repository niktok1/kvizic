package io.ntole.kvizic

import android.app.Application
import io.ntole.kvizic.analytics.AnalyticsSettings
import io.ntole.kvizic.di.initKoin
import io.ntole.kvizic.services.GoogleServiceSettings
import io.ntole.kvizic.services.androidDeviceServices
import org.koin.android.ext.koin.androidContext

/**
 * Exists solely to start DI with an Android `Context`, the environment this build's flavor was made for,
 * the analytics project the build names, and Play Games where the build has its ids.
 *
 * Those are what shared code cannot obtain for itself: the context is the token storage's, and
 * `BuildConfig` is generated in this module, one per flavor, with the PostHog key and host from the
 * build's `kvizic.posthog.*` settings, the app's version, and its build number, the `versionCode`, which
 * every request names. That is exactly the kind of thing a platform entry point is for.
 */
class KvizicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            environmentName = BuildConfig.KVIZIC_ENV,
            analytics =
                AnalyticsSettings(
                    key = BuildConfig.POSTHOG_KEY,
                    host = BuildConfig.POSTHOG_HOST,
                    appVersion = BuildConfig.VERSION_NAME,
                ),
            build = BuildConfig.VERSION_CODE,
            device =
                androidDeviceServices(
                    application = this,
                    settings =
                        GoogleServiceSettings(
                            playGamesAppId = BuildConfig.PLAY_GAMES_APP_ID,
                            playGamesServerClientId = BuildConfig.PLAY_GAMES_SERVER_CLIENT_ID,
                        ),
                ),
        ) {
            androidContext(this@KvizicApplication)
        }
    }
}
