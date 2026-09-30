package io.ntole.kvizic.di

import org.koin.core.module.Module

/**
 * Bindings only a platform can provide: its [io.ntole.kvizic.core.network.TokenStorage]. On the desktop
 * that storage is the profile's `KVIZIC_PROFILE` names, so several instances on one machine are separate
 * players.
 *
 * One of the few places per-platform code lives in this module, beside Android's back
 * ([io.ntole.kvizic.navigation.SystemBack]), Android's rotation, which the analytics let pass
 * (`rememberConfigurationChanging`), the share sheet, the update button and Android's Google services
 * behind the domain's ports (`androidDeviceServices`, handed to [initKoin] as [DeviceServices]):
 * everything else is common code.
 */
expect fun platformModule(): Module
