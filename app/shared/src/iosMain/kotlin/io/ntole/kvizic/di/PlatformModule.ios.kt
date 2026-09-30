package io.ntole.kvizic.di

import io.ntole.kvizic.core.network.IosTokenStorage
import io.ntole.kvizic.core.network.TokenStorage
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module =
    module {
        single<TokenStorage> { IosTokenStorage() }
    }
