package io.ntole.kvizic.di

import io.ntole.kvizic.core.network.AndroidTokenStorage
import io.ntole.kvizic.core.network.TokenStorage
import io.ntole.kvizic.region.AndroidDeviceRegion
import io.ntole.kvizic.region.DeviceRegion
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<TokenStorage> { AndroidTokenStorage(androidContext()) }
        single<DeviceRegion> { AndroidDeviceRegion(androidContext()) }
    }
