package io.ntole.kvizic.core.data.di

import io.ktor.client.HttpClient
import io.ntole.kvizic.core.data.account.DefaultAccountRepository
import io.ntole.kvizic.core.data.lobby.DefaultLobbySession
import io.ntole.kvizic.core.data.lobby.DefaultPublicLobbyRepository
import io.ntole.kvizic.core.data.player.DefaultPlayerRepository
import io.ntole.kvizic.core.data.playgames.DefaultPlayGamesRepository
import io.ntole.kvizic.core.data.session.DefaultSessionRepository
import io.ntole.kvizic.core.data.session.PlayGamesSettled
import io.ntole.kvizic.core.data.topic.DefaultTopicRepository
import io.ntole.kvizic.core.domain.account.AccountRepository
import io.ntole.kvizic.core.domain.account.DeleteAccount
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.lobby.LobbySession
import io.ntole.kvizic.core.domain.lobby.PublicLobbyRepository
import io.ntole.kvizic.core.domain.player.GetProfile
import io.ntole.kvizic.core.domain.player.PlayerRepository
import io.ntole.kvizic.core.domain.player.SetAvatar
import io.ntole.kvizic.core.domain.playgames.LinkPlayGames
import io.ntole.kvizic.core.domain.playgames.PlayGames
import io.ntole.kvizic.core.domain.playgames.PlayGamesRepository
import io.ntole.kvizic.core.domain.session.CurrentSession
import io.ntole.kvizic.core.domain.session.SessionRepository
import io.ntole.kvizic.core.domain.topic.GetTopics
import io.ntole.kvizic.core.domain.topic.TopicRepository
import io.ntole.kvizic.core.domain.update.AppUpdate
import io.ntole.kvizic.core.network.ClientBuild
import io.ntole.kvizic.core.network.KvizicHttpClient
import io.ntole.kvizic.core.network.SessionStore
import io.ntole.kvizic.core.network.TokenStorage
import io.ntole.kvizic.core.network.UpgradeSignal
import io.ntole.kvizic.core.network.analytics.PostHogAnalytics
import io.ntole.kvizic.core.network.analytics.PostHogConfig
import io.ntole.kvizic.core.network.api.AuthApi
import io.ntole.kvizic.core.network.api.LobbyApi
import io.ntole.kvizic.core.network.api.PlayerApi
import io.ntole.kvizic.core.network.api.TopicApi
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import io.ntole.kvizic.core.network.realtime.KtorPlayTransport
import io.ntole.kvizic.core.network.realtime.PlayTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The platform's wiring, the same for every game of the developer's: the HTTP client, the session and
 * its recovery, analytics, the update signal, Play Games and the account's deletion. The game's own
 * repositories are [gameDataModule]'s, loaded beside this one.
 *
 * Expects a [TokenStorage] to be registered already: the one binding only a platform can supply, so it
 * comes from `:app:shared`'s platform module.
 *
 * @param environment the server environment the build targets: every request goes to its
 *   [KvizicEnvironment.apiBaseUrl], and its session is kept apart from every other environment's in that
 *   storage ([SessionStore]), as its analytics id is.
 * @param analytics the PostHog project the build sends its analytics to, or none, when nothing is sent:
 *   the [Analytics] bound keeps only the player's switch then.
 * @param build the build every request names, or none, for a build that could not read its own number,
 *   whose requests name nothing. An answer that the build is too old raises the [AppUpdate] bound,
 *   whichever call it was.
 * @param playGames Google Play Games Services on this device: an Android build's that has it set up, and
 *   [PlayGames.None] everywhere else.
 */
public fun platformDataModule(
    environment: KvizicEnvironment,
    analytics: PostHogConfig?,
    build: ClientBuild?,
    playGames: PlayGames = PlayGames.None,
): Module =
    module {
        single { SessionStore(get<TokenStorage>(), environment) }
        single<Analytics> { PostHogAnalytics(config = analytics, storage = get(), environment = environment) }
        single { UpgradeSignal() }
        single<AppUpdate> { get<UpgradeSignal>() }
        single<HttpClient> {
            KvizicHttpClient.create(
                baseUrl = environment.apiBaseUrl,
                sessionStore = get(),
                build = build,
                upgrade = get(),
            )
        }

        single { AuthApi(get()) }
        // Here rather than in the game's module: deleting the account is the platform's.
        single { PlayerApi(get()) }

        // Bound as the concrete type as well: repositories recover a dead session through
        // withSessionRecovery, which is recovery machinery and deliberately not on the domain interface.
        single {
            DefaultSessionRepository(
                authApi = get(),
                sessionStore = get(),
                playGamesSettled = PlayGamesSettled(get<TokenStorage>(), environment),
            )
        }
        single<SessionRepository> { get<DefaultSessionRepository>() }
        single<CurrentSession> { get<DefaultSessionRepository>() }

        single<PlayGamesRepository> { DefaultPlayGamesRepository(api = get(), session = get()) }
        single<AccountRepository> { DefaultAccountRepository(auth = get(), players = get(), session = get()) }

        factory { DeleteAccount(accounts = get(), analytics = get()) }
        // Once for the app: the launch's sign-in and a tap on a button take turns.
        single { LinkPlayGames(playGames = playGames, link = get(), session = get(), analytics = get()) }
    }

/**
 * The game's own repositories over the platform's client and session, which [platformDataModule] binds:
 * the player's profile, the topics, and the lobbies with their realtime socket, which goes to
 * [environment]'s API host and names [build] in its hello.
 *
 * The lobby session lives as long as the app, in [appScope]: a lobby outlasts every screen.
 */
public fun gameDataModule(
    environment: KvizicEnvironment,
    build: ClientBuild?,
    appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): Module =
    module {
        single { TopicApi(get()) }
        single { LobbyApi(get()) }
        single<PlayTransport> { KtorPlayTransport(environment.apiBaseUrl, build) }

        single<PlayerRepository> { DefaultPlayerRepository(api = get(), session = get()) }
        single<TopicRepository> { DefaultTopicRepository(api = get()) }
        single<PublicLobbyRepository> { DefaultPublicLobbyRepository(api = get(), session = get()) }
        single<LobbySession> {
            DefaultLobbySession(api = get(), transport = get(), session = get(), scope = appScope, upgrade = get())
        }

        factory { GetProfile(players = get(), session = get()) }
        factory { SetAvatar(players = get(), session = get()) }
        factory { GetTopics(topics = get()) }
    }
