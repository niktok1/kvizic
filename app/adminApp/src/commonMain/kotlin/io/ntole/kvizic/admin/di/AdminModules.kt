package io.ntole.kvizic.admin.di

import io.ntole.kvizic.admin.ModerationViewModel
import io.ntole.kvizic.core.data.di.moderationDataModule
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Starts Koin for the moderation app against the environment [environmentName] names, which
 * [KvizicEnvironment.parse] reads, so a bad name stops the app at launch. Returns the environment, which
 * the app names on every screen.
 */
fun initAdminKoin(environmentName: String?): KvizicEnvironment {
    val environment = KvizicEnvironment.parse(environmentName)
    startKoin { modules(adminModules(environment)) }
    return environment
}

/** The moderator's client alone ([moderationDataModule]): no player session, no bearer, no guest. */
fun adminModules(environment: KvizicEnvironment): List<Module> =
    listOf(
        moderationDataModule(environment),
        module {
            single { environment }
            viewModelOf(::ModerationViewModel)
        },
    )
