package io.ntole.kvizic.di

import io.ntole.kvizic.about.AboutViewModel
import io.ntole.kvizic.about.AppVersion
import io.ntole.kvizic.analytics.AnalyticsSettings
import io.ntole.kvizic.analytics.UsageTracker
import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.playgames.LinkPlayGames
import io.ntole.kvizic.core.domain.session.CurrentSession
import io.ntole.kvizic.core.domain.update.AppUpdate
import io.ntole.kvizic.core.network.InMemoryTokenStorage
import io.ntole.kvizic.core.network.SessionStore
import io.ntole.kvizic.core.network.TokenStorage
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import io.ntole.kvizic.home.HomeViewModel
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.LanguageViewModel
import io.ntole.kvizic.services.AppServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.Koin
import org.koin.core.context.stopKoin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The real data and UI modules, with only the platform bindings stood in for. A binding that is missing
 * only shows when a screen first asks for its ViewModel, so this is the difference between a failing test
 * and an app that dies on launch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppModuleTest {
    @BeforeTest
    fun setUp() {
        // Each ViewModel's init launches on viewModelScope, which runs on Dispatchers.Main.
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        // For the tests that start the app's own Koin; stopping one that never started does nothing.
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun `every ViewModel resolves from the real modules`() {
        val koin = koinFor(KvizicEnvironment.LOCAL)

        koin.get<HomeViewModel>()
        koin.get<AboutViewModel>()
        koin.get<LanguageViewModel>()
        // What App asks for before any screen: whether the server refused this build, the analytics and
        // their tracker, and the About screen's version and account id.
        koin.get<AppUpdate>()
        koin.get<Analytics>()
        koin.get<UsageTracker>()
        koin.get<AppVersion>()
        koin.get<CurrentSession>()
    }

    /** What runs by itself, and a build with no Play Games has it all the same, doing nothing. */
    @Test
    fun `what the app does by itself resolves from the real modules`() {
        val koin = koinFor(KvizicEnvironment.LOCAL)

        koin.get<AppServices>()
        assertFalse(koin.get<LinkPlayGames>().available, "no platform services: no Play Games")
    }

    @Test
    fun `each environment is the one bound for the screens`() {
        KvizicEnvironment.entries.forEach { environment ->
            assertEquals(environment, koinFor(environment).get<KvizicEnvironment>())
        }
    }

    @Test
    fun `each environment keeps its own session in the storage the platform shares between them`() =
        runTest {
            val storage = InMemoryTokenStorage()
            val dev = koinFor(KvizicEnvironment.DEV, storage).get<SessionStore>()
            val prod = koinFor(KvizicEnvironment.PROD, storage).get<SessionStore>()

            dev.write(DEV_SESSION)

            assertEquals(DEV_SESSION, dev.read())
            assertNull(prod.read())
        }

    /**
     * On desktop, iOS and web every environment's build shares one storage, and the language is the
     * player's, not the server's: a build for one shows the language picked in another.
     */
    @Test
    fun `the language is one for the device whatever the environment`() =
        runTest {
            val storage = InMemoryTokenStorage()

            koinFor(KvizicEnvironment.DEV, storage).get<LanguageViewModel>().select(Language.ENGLISH)
            testScheduler.advanceUntilIdle()

            KvizicEnvironment.entries.forEach { environment ->
                assertEquals(Language.ENGLISH, koinFor(environment, storage).get<LanguageViewModel>().language.value)
            }
        }

    /** The language shares the sessions' storage, so neither may write over the other. */
    @Test
    fun `the language and every session are kept apart in the one storage`() =
        runTest {
            val storage = InMemoryTokenStorage()
            KvizicEnvironment.entries.forEach { environment ->
                koinFor(environment, storage).get<SessionStore>().write(DEV_SESSION)
            }

            koinFor(KvizicEnvironment.PROD, storage).get<LanguageViewModel>().select(Language.SERBIAN_LATIN)
            testScheduler.advanceUntilIdle()

            KvizicEnvironment.entries.forEach { environment ->
                assertEquals(DEV_SESSION, koinFor(environment, storage).get<SessionStore>().read(), "$environment")
            }
            val language = koinFor(KvizicEnvironment.PROD, storage).get<LanguageViewModel>().language
            assertEquals(Language.SERBIAN_LATIN, language.value)
        }

    @Test
    fun `initKoin starts the environment each entry point's name names`() {
        mapOf(
            "local" to KvizicEnvironment.LOCAL,
            "dev" to KvizicEnvironment.DEV,
            "prod" to KvizicEnvironment.PROD,
            null to KvizicEnvironment.LOCAL,
        ).forEach { (name, environment) ->
            // Only the environment is resolved: the platform's own storage is never built, so nothing of this
            // machine's is read or written.
            initKoin(environmentName = name, analytics = NO_ANALYTICS, build = 100)

            assertEquals(environment, KoinPlatform.getKoin().get<KvizicEnvironment>(), "\"$name\"")
            stopKoin()
        }
    }

    @Test
    fun `an environment name it does not know stops the app before Koin starts`() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                initKoin(environmentName = "staging", analytics = NO_ANALYTICS, build = 100)
            }

        assertTrue("\"staging\"" in failure.message.orEmpty(), failure.message)
        assertNull(KoinPlatform.getKoinOrNull())
    }

    @Test
    fun `a PostHog host that is none stops the app before Koin starts`() {
        val analytics = AnalyticsSettings(key = "phc_key", host = "eu posthog com", appVersion = "0.1.0")

        val failure =
            assertFailsWith<IllegalArgumentException> { initKoin(environmentName = "dev", analytics, build = 100) }

        assertTrue("eu posthog com" in failure.message.orEmpty(), failure.message)
        assertNull(KoinPlatform.getKoinOrNull())
    }

    /** No key, as every test and CI build has: the switch is there, and nothing is kept or sent. */
    @Test
    fun `the analytics bound without a key keep nothing`() {
        val storage = InMemoryTokenStorage()
        val analytics = koinFor(KvizicEnvironment.DEV, storage).get<Analytics>()

        analytics.track("app_opened")
        analytics.flush()

        assertTrue(analytics.enabled.value)
        assertNull(storage.read("kvizic.analytics.id.dev"))
    }

    private fun koinFor(
        environment: KvizicEnvironment,
        storage: TokenStorage = InMemoryTokenStorage(),
    ): Koin {
        val platform = module { single<TokenStorage> { storage } }
        return koinApplication {
            modules(listOf(platform) + appModules(environment, analytics = null, version = AppVersion("0.1.0", null)))
        }.koin
    }

    private companion object {
        val NO_ANALYTICS = AnalyticsSettings(key = null, host = null, appVersion = "")

        val DEV_SESSION =
            SessionDto(
                playerId = "dev-player",
                accessToken = "dev-access",
                refreshToken = "dev-refresh",
                accessTokenExpiresInSeconds = 900,
            )
    }
}
