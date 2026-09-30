package io.ntole.kvizic.di

import io.ntole.kvizic.core.network.JvmTokenStorage
import io.ntole.kvizic.core.network.TokenStorage
import io.ntole.kvizic.desktopProfileName
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The storage of the profile `KVIZIC_PROFILE` names, the game's own node for none. The name is checked
 * here, as Koin starts, so a mistyped one stops the app at launch; the storage itself is made only when
 * something first asks for it, so starting Koin touches none of this machine's preferences.
 */
actual fun platformModule(): Module {
    val profile = JvmTokenStorage.checkedProfile(desktopProfileName())
    return module {
        single<TokenStorage> { JvmTokenStorage.forProfile(profile) }
    }
}
