package io.ntole.kvizic.services

import android.app.Application
import android.util.Log
import com.google.android.gms.games.PlayGamesSdk
import io.ntole.kvizic.core.domain.playgames.PlayGames
import io.ntole.kvizic.di.DeviceServices

/**
 * The platform services an Android build has, for [io.ntole.kvizic.di.initKoin]: Play Games where
 * [settings] name its ids, started here, as its SDK asks to be as the application is made (its own
 * provider, which would start it on every build, is taken out of the manifest). Without its ids it is off,
 * which the log says once. Called from `KvizicApplication.onCreate`.
 */
fun androidDeviceServices(
    application: Application,
    settings: GoogleServiceSettings,
): DeviceServices {
    settings.offLines().forEach { line -> Log.i(LOG_TAG, line) }
    val activities = ActivityTracker().also(application::registerActivityLifecycleCallbacks)
    return DeviceServices(playGames = playGamesOf(application, activities, settings))
}

private fun playGamesOf(
    application: Application,
    activities: ActivityTracker,
    settings: GoogleServiceSettings,
): PlayGames {
    if (!settings.playGamesOn) return PlayGames.None
    PlayGamesSdk.initialize(application)
    return AndroidPlayGames(activities, settings.playGamesServerClientId)
}

internal const val LOG_TAG = "Kvizic"
