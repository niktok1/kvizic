package io.ntole.kvizic.di

import io.ntole.kvizic.about.AboutViewModel
import io.ntole.kvizic.about.AppVersion
import io.ntole.kvizic.analytics.AnalyticsSettings
import io.ntole.kvizic.analytics.UsageTracker
import io.ntole.kvizic.core.data.di.gameDataModule
import io.ntole.kvizic.core.data.di.platformDataModule
import io.ntole.kvizic.core.network.ClientBuild
import io.ntole.kvizic.core.network.analytics.PostHogConfig
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import io.ntole.kvizic.home.HomeViewModel
import io.ntole.kvizic.language.LanguageViewModel
import io.ntole.kvizic.room.PublicRoomsViewModel
import io.ntole.kvizic.room.RoomViewModel
import io.ntole.kvizic.services.AppServices
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module
import kotlin.time.TimeSource

// Internal, not private, so a test can resolve every ViewModel from the real graph.
internal val uiModule =
    module {
        viewModelOf(::HomeViewModel)
        viewModelOf(::AboutViewModel)
        viewModelOf(::LanguageViewModel)
        viewModelOf(::RoomViewModel)
        viewModelOf(::PublicRoomsViewModel)
        // What the analytics time with: how long a screen or the app was shown.
        single<TimeSource.WithComparableMarks> { TimeSource.Monotonic }
        // One for the app's life, as the analytics are: a rotation's new activity finds it.
        single { UsageTracker(analytics = get(), timeSource = get()) }
        // What the app does by itself, as long as it runs: one for its life too.
        single { AppServices(linkPlayGames = get()) }
    }

/**
 * Starts Koin for the server environment [environmentName] names, by [KvizicEnvironment.parse]: no name is
 * [KvizicEnvironment.LOCAL], and one it does not know stops the app here, before anything has started.
 * [analytics] is the PostHog project the build sends to, by [PostHogConfig.of]: no key is none, and a host
 * that is none stops the app here too. [build] is the build number every request names, or null for a
 * build that could not read its own, whose requests then name none.
 *
 * Called once per process from each platform's entry point, which names the environment it was built or
 * started for: Android's product flavor, the web build's `-Pkvizic.env`, desktop's `KVIZIC_ENV` variable,
 * the iOS app's Info.plist; and the analytics and the build number each reads from the same place. None
 * has a default, so no entry point can leave one out by accident and end up on LOCAL, or send nowhere;
 * `null` is for a build that set none. They are plain strings so that `:core:network` stays off the entry
 * points' classpaths.
 *
 * [device] is what only the platform's own services can do: an Android build's, and [DeviceServices.None]
 * everywhere else.
 *
 * [appDeclaration] is how Android hands in its `Context`, which the shared code otherwise has no way to
 * obtain, which is also why Koin is an `api` dependency of this module rather than an implementation
 * detail.
 */
fun initKoin(
    environmentName: String?,
    analytics: AnalyticsSettings,
    build: Int?,
    device: DeviceServices = DeviceServices.None,
    appDeclaration: KoinAppDeclaration = {},
) {
    val environment = KvizicEnvironment.parse(environmentName)
    val posthog = PostHogConfig.of(analytics.key, analytics.host, analytics.appVersion)
    val platform = platformModule()
    startKoin {
        appDeclaration()
        modules(platform)
        modules(appModules(environment, posthog, AppVersion(name = analytics.appVersion, number = build), device))
    }
}

/**
 * Every module but the platform's, for [environment]: the data modules send every request to its URL, and
 * the environment is bound for the screens that show it. [analytics] is where the analytics go, none
 * sending nothing, [version] the app's, whose build number every request names (none naming nothing) and
 * the About screen shows, and [device] the platform's own services, none by default. Internal, not
 * private, so a test can load them as [initKoin] does.
 */
internal fun appModules(
    environment: KvizicEnvironment,
    analytics: PostHogConfig?,
    version: AppVersion,
    device: DeviceServices = DeviceServices.None,
): List<Module> =
    listOf(
        module {
            single { environment }
            single { version }
        },
        platformDataModule(environment, analytics, ClientBuild.of(version.number), playGames = device.playGames),
        gameDataModule(environment, ClientBuild.of(version.number)),
        uiModule,
    )
