package io.ntole.kvizic.core.data.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.domain.account.AccountRepository
import io.ntole.kvizic.core.domain.account.DeleteAccount
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.moderation.ModerationRepository
import io.ntole.kvizic.core.domain.player.GetProfile
import io.ntole.kvizic.core.domain.player.PlayerRepository
import io.ntole.kvizic.core.domain.player.SetAvatar
import io.ntole.kvizic.core.domain.playgames.LinkPlayGames
import io.ntole.kvizic.core.domain.playgames.PlayGamesRepository
import io.ntole.kvizic.core.domain.session.CurrentSession
import io.ntole.kvizic.core.domain.session.SessionRepository
import io.ntole.kvizic.core.domain.topic.GetTopics
import io.ntole.kvizic.core.domain.topic.TopicRepository
import io.ntole.kvizic.core.domain.update.AppUpdate
import io.ntole.kvizic.core.network.ApiException
import io.ntole.kvizic.core.network.ClientBuild
import io.ntole.kvizic.core.network.InMemoryTokenStorage
import io.ntole.kvizic.core.network.TokenStorage
import io.ntole.kvizic.core.network.api.AuthApi
import io.ntole.kvizic.core.network.api.ModerationApi
import io.ntole.kvizic.core.network.api.TopicApi
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import kotlinx.coroutines.test.runTest
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The data modules as the app loads them. Where a request goes is read off it as it leaves, after every
 * plugin has had its say and before the engine sends it: the one place a build could still talk to a
 * server nobody chose.
 */
class DataModuleTest {
    @Test
    fun `each environment's requests go to its own server`() =
        runTest {
            KvizicEnvironment.entries.forEach { environment ->
                val koin = koin(environment)
                val client = koin.get<HttpClient>()
                client.plugin(HttpSend).intercept { request -> throw NotSent(request.url.buildString()) }

                val guest = assertFailsWith<NotSent>(environment.name) { koin.get<AuthApi>().guest() }
                val topics = assertFailsWith<NotSent>(environment.name) { koin.get<TopicApi>().all() }

                assertEquals(environment.apiBaseUrl + KvizicApi.Paths.AUTH_GUEST, guest.url, environment.name)
                assertEquals(environment.apiBaseUrl + KvizicApi.Paths.TOPICS, topics.url, environment.name)
                client.close()
                koin.close()
            }
        }

    /** The game's every request names its build, so a server can refuse one too old. */
    @Test
    fun `every request names the build`() =
        runTest {
            val koin = koin(KvizicEnvironment.LOCAL, build = ClientBuild(KvizicApi.ClientPlatform.WEB, 100))
            val client = koin.get<HttpClient>()
            client.plugin(HttpSend).intercept { request ->
                throw NotSent(
                    request.url.buildString(),
                    platform = request.headers[KvizicApi.Headers.CLIENT_PLATFORM],
                    version = request.headers[KvizicApi.Headers.CLIENT_VERSION],
                )
            }

            val request = assertFailsWith<NotSent> { koin.get<TopicApi>().all() }

            assertEquals(KvizicApi.ClientPlatform.WEB to "100", request.platform to request.version)
            client.close()
            koin.close()
        }

    /**
     * The client the game's calls go through is the one that raises the update the app shows. The answer
     * is made by a client of the test's own, since the module's reaches no engine a test can stand in for,
     * and thrown where the engine would answer.
     */
    @Test
    fun `an answer that the build is too old raises the update the app shows`() =
        runTest {
            val koin = koin(KvizicEnvironment.LOCAL, build = ClientBuild.of(100))
            val update = koin.get<AppUpdate>()
            val answerer = HttpClient(MockEngine { respond("", HttpStatusCode.UpgradeRequired) })
            val tooOld = answerer.get("https://kvizic.test/")
            koin.get<HttpClient>().plugin(HttpSend).intercept { throw ClientRequestException(tooOld, "") }

            assertFalse(update.required.value)
            assertFailsWith<ApiException> { koin.get<TopicApi>().all() }

            assertTrue(update.required.value)
            answerer.close()
            koin.close()
        }

    /**
     * The Play Games sign-in the platform hands in, [io.ntole.kvizic.core.domain.playgames.PlayGames.None]
     * unless one does, the account's deletion, and the session watched without minting: nothing is
     * stored, and nothing sent.
     */
    @Test
    fun `the platform module binds the session the sign-in and the deletion`() {
        val storage = InMemoryTokenStorage()
        val koin = koin(KvizicEnvironment.DEV, storage = storage)

        assertFalse(koin.get<LinkPlayGames>().available)
        assertFalse(koin.get<PlayGamesRepository>().isSettled())
        assertNotNull(koin.get<AccountRepository>())
        assertNotNull(koin.get<DeleteAccount>())
        assertNull(koin.get<CurrentSession>().current())
        assertSame<Any>(koin.get<SessionRepository>(), koin.get<CurrentSession>(), "one session for both")
        koin.close()
    }

    @Test
    fun `the game module binds the profile and the topics`() {
        val koin = koin(KvizicEnvironment.LOCAL)

        assertNotNull(koin.get<PlayerRepository>())
        assertNotNull(koin.get<GetProfile>())
        assertNotNull(koin.get<SetAvatar>())
        assertNotNull(koin.get<TopicRepository>())
        assertNotNull(koin.get<GetTopics>())
        koin.close()
    }

    /** The platform's wiring stands on its own: nothing of the game's comes with it. */
    @Test
    fun `the platform module alone binds nothing of the game's`() {
        val koin =
            koinApplication {
                modules(
                    module { single<TokenStorage> { InMemoryTokenStorage() } },
                    platformDataModule(KvizicEnvironment.LOCAL, analytics = null, build = null),
                )
            }.koin

        assertNull(koin.getOrNull<PlayerRepository>())
        assertNull(koin.getOrNull<TopicRepository>())
        assertNull(koin.getOrNull<GetProfile>())
        assertNotNull(koin.get<LinkPlayGames>())
        koin.close()
    }

    /**
     * The moderator's client goes to its environment's server with no session of a player's: no bearer, no
     * build, and nothing a guest could be minted with. The game's modules bind nothing of it.
     */
    @Test
    fun `the moderation module talks to its server as nobody and the game cannot moderate`() =
        runTest {
            KvizicEnvironment.entries.forEach { environment ->
                val koin = koinApplication { modules(moderationDataModule(environment)) }.koin
                val client = koin.get<HttpClient>()
                client.plugin(HttpSend).intercept { request ->
                    throw NotSent(
                        request.url.buildString(),
                        platform = request.headers[KvizicApi.Headers.CLIENT_PLATFORM],
                        version = request.headers[HttpHeaders.Authorization],
                    )
                }
                val sent = assertFailsWith<NotSent> { koin.get<ModerationApi>().overview("t") }

                assertEquals(environment.apiBaseUrl + KvizicApi.Paths.ADMIN_OVERVIEW, sent.url)
                assertNull(sent.platform)
                assertNull(sent.version, "a bearer went with it")
                assertNotNull(koin.get<ModerationRepository>())
                assertNotNull(koin.get<GetTopics>())
                assertNull(koin.getOrNull<SessionRepository>())
                client.close()
                koin.close()
            }
            val game = koin(KvizicEnvironment.LOCAL)
            assertNull(game.getOrNull<ModerationRepository>())
            game.close()
        }

    /** No key, as in every test and CI build: the switch is there, and nothing is kept or sent. */
    @Test
    fun `analytics without a key keep nothing`() =
        runTest {
            val storage = InMemoryTokenStorage()
            val koin = koin(KvizicEnvironment.LOCAL, storage = storage)

            val analytics = koin.get<Analytics>()
            analytics.track("app_opened")
            analytics.identify("player-1")
            analytics.flush()

            assertEquals(true, analytics.enabled.value)
            assertNull(storage.read("kvizic.analytics.id.local"))
            koin.close()
        }

    private fun koin(
        environment: KvizicEnvironment,
        build: ClientBuild? = null,
        storage: TokenStorage = InMemoryTokenStorage(),
    ): Koin =
        koinApplication {
            modules(
                module { single<TokenStorage> { storage } },
                platformDataModule(environment, analytics = null, build = build),
                gameDataModule(environment, build),
            )
        }.koin

    /** Stops a request at the engine's door, carrying the URL it was about to go to and the build it names. */
    private class NotSent(
        val url: String,
        val platform: String? = null,
        val version: String? = null,
    ) : Exception(url)
}
