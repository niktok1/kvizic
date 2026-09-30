package io.ntole.kvizic.di

import io.ntole.kvizic.core.domain.playgames.PlayGames

/**
 * What only a platform's own services can do, handed to [initKoin] by its entry point, since each is made
 * there, before Koin starts, from what the platform alone has: Google Play Games Services on an Android
 * build that has it set up. Every other platform, and every test, has [None].
 */
data class DeviceServices(
    val playGames: PlayGames = PlayGames.None,
) {
    companion object {
        /** No platform services: desktop, iOS and the web, and the tests. */
        val None: DeviceServices = DeviceServices()
    }
}
