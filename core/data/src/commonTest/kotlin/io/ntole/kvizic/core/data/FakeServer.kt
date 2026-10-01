package io.ntole.kvizic.core.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.auth.PlayGamesSignInRequest
import io.ntole.kvizic.core.auth.RefreshRequest
import io.ntole.kvizic.core.auth.SessionDto
import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.lobby.LobbyListDto
import io.ntole.kvizic.core.lobby.TicketDto
import io.ntole.kvizic.core.network.KvizicJson
import io.ntole.kvizic.core.player.Avatars
import io.ntole.kvizic.core.player.NameSource
import io.ntole.kvizic.core.player.PlayerStatsDto
import io.ntole.kvizic.core.player.ProfileDto
import io.ntole.kvizic.core.player.SetAvatarRequest
import io.ntole.kvizic.core.report.ReportQuestionRequest
import io.ntole.kvizic.core.topic.TopicDto
import io.ntole.kvizic.core.topic.TopicListDto
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Just enough of the server, behind a [MockEngine], to put sessions through the real client: it mints
 * guests, rotates refresh tokens, signs in with Play Games, logs out and deletes accounts, answers a
 * player's profile and avatar, and refuses any of those from a player it does not know, the state after a
 * dev server restarts with an empty database. It lists the topics to anybody, a session or none.
 */
internal class FakeServer {
    private val lock = Mutex()
    private val players = mutableSetOf<String>()
    private val avatars = mutableMapOf<String, String>()
    private val liveRefreshTokens = mutableMapOf<String, String>()
    private var rotations = 0

    var guestsMinted = 0
        private set

    /** When set, every refresh is refused with this status and code, and rotates nothing. */
    var refuseRefreshesWith: Pair<HttpStatusCode, ErrorCode>? = null

    /** How many refreshes arrived, refused or not. */
    var refreshesSent = 0
        private set

    /** The `Authorization` header of every profile read, in arrival order. */
    val profilesSentAs = mutableListOf<String?>()

    /** The `Authorization` header and avatar of every avatar picked, in arrival order. */
    val avatarsSentAs = mutableListOf<Pair<String?, String>>()

    /** The `Authorization` header of every logout, in arrival order. */
    val logoutsSentAs = mutableListOf<String?>()

    /** When set, every logout is refused with this status and code, whoever sends it. */
    var refuseLogoutsWith: Pair<HttpStatusCode, ErrorCode>? = null

    /**
     * The Play Games player each server auth code names, as Google would answer: a code not here is one
     * Google refuses. Each works once, as Google's do.
     */
    val playGamesCodes = mutableMapOf<String, String>()

    /** The player each Play Games player is linked to, by the Play Games player's id. */
    val playGamesLinks = mutableMapOf<String, String>()

    /** The `Authorization` header and code of every Play Games sign-in, in arrival order. */
    val playGamesSentAs = mutableListOf<Pair<String?, String>>()

    /** When set, every Play Games sign-in is refused with this status and code, whoever sends it. */
    var refusePlayGamesWith: Pair<HttpStatusCode, ErrorCode>? = null

    /** Run once a Play Games sign-in has asked Google and before it answers: a change of player, say. */
    var whilePlayGamesExchanges: suspend () -> Unit = {}

    /** The `Authorization` header of every account deletion, in arrival order. */
    val deletionsSentAs = mutableListOf<String?>()

    /** When set, every account deletion is refused with this status and code, and deletes nothing. */
    var refuseDeletionsWith: Pair<HttpStatusCode, ErrorCode>? = null

    /** What a read of the topics answers, whoever sends it: [TOPICS] unless a test says otherwise. */
    var topics: TopicListDto = TOPICS

    /** When set, every read of the topics is refused with this status and code. */
    var refuseTopicsWith: Pair<HttpStatusCode, ErrorCode>? = null

    /** The `Authorization` header of every read of the topics, in arrival order. */
    val topicsSentAs = mutableListOf<String?>()

    /** The lobby every create, Quick play and solo run seats in, by its code. */
    var lobbyCode: String = "482915"

    /** How many tickets went out, and to whom, by the `Authorization` header. */
    val ticketsSentTo = mutableListOf<String?>()

    /** When set, every lobby join is refused with this status and code. */
    var refuseJoinsWith: Pair<HttpStatusCode, ErrorCode>? = null

    /** What a read of the public lobbies answers. */
    var publicLobbies: LobbyListDto = LobbyListDto()

    /** Every report sent, with the `Authorization` header it went with. */
    val reportsSentAs = mutableListOf<Pair<String?, ReportQuestionRequest>>()

    /** The questions a report may name; any other is QUESTION_NOT_FOUND. */
    var reportable: Set<String> = setOf("q1")

    val engine = MockEngine { request -> lock.withLock { handle(request) } }

    /** The same server on [dispatcher]: a test's own, so its calls run on the test's virtual clock. */
    fun engineOn(dispatcher: CoroutineDispatcher): MockEngine =
        MockEngine(
            MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request -> lock.withLock { handle(request) } }
            },
        )

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData =
        when (request.url.encodedPath) {
            KvizicApi.Paths.AUTH_GUEST -> {
                guestsMinted++
                issueSession("guest$guestsMinted")
            }

            KvizicApi.Paths.AUTH_REFRESH -> {
                refresh(request)
            }

            KvizicApi.Paths.AUTH_PLAY_GAMES -> {
                playGames(request)
            }

            KvizicApi.Paths.AUTH_LOGOUT -> {
                val authorization = request.headers[HttpHeaders.Authorization]
                logoutsSentAs += authorization
                val refusal = refuseLogoutsWith
                when {
                    refusal != null -> {
                        respondErrorDto(refusal.first, refusal.second)
                    }

                    authorization.player() !in players -> {
                        respondErrorDto(
                            HttpStatusCode.Unauthorized,
                            ErrorCode.UNAUTHORIZED,
                        )
                    }

                    else -> {
                        respond("", HttpStatusCode.NoContent)
                    }
                }
            }

            KvizicApi.Paths.ME -> {
                val authorization = request.headers[HttpHeaders.Authorization]
                profilesSentAs += authorization
                val player = authorization.player()
                if (player != null && player in players) {
                    respondJson(KvizicJson.encodeToString(profileOf(player)))
                } else {
                    respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
                }
            }

            KvizicApi.Paths.MY_AVATAR -> {
                avatar(request)
            }

            KvizicApi.Paths.REPORTS -> {
                report(request)
            }

            KvizicApi.Paths.ME_DELETION -> {
                delete(request)
            }

            // To anybody, with or without a session, as the server's does.
            KvizicApi.Paths.TOPICS -> {
                topicsSentAs += request.headers[HttpHeaders.Authorization]
                val refusal = refuseTopicsWith
                if (refusal != null) {
                    respondErrorDto(refusal.first, refusal.second)
                } else {
                    respondJson(KvizicJson.encodeToString(topics))
                }
            }

            KvizicApi.Paths.LOBBIES -> {
                if (request.method == HttpMethod.Get) {
                    if (request.headers[HttpHeaders.Authorization].player() in players) {
                        respondJson(KvizicJson.encodeToString(publicLobbies))
                    } else {
                        respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
                    }
                } else {
                    ticket(request)
                }
            }

            KvizicApi.Paths.QUICK_PLAY, KvizicApi.Paths.SOLO_RUNS -> {
                ticket(request)
            }

            KvizicApi.Paths.LOBBY_JOINS -> {
                val refusal = refuseJoinsWith
                if (refusal != null) respondErrorDto(refusal.first, refusal.second) else ticket(request)
            }

            else -> {
                error("FakeServer has no route for ${request.url}")
            }
        }

    /** A ticket for [lobbyCode], `ticket-<n>`, to a player it knows. */
    private fun MockRequestHandleScope.ticket(request: HttpRequestData): HttpResponseData {
        val authorization = request.headers[HttpHeaders.Authorization]
        if (authorization.player() !in
            players
        ) {
            return respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
        }
        ticketsSentTo += authorization
        val ticket =
            TicketDto(
                lobbyId = "lobby-$lobbyCode",
                code = lobbyCode,
                ticket = "ticket-${ticketsSentTo.size}",
                ticketExpiresInMs = 30_000,
            )
        return respondJson(KvizicJson.encodeToString(ticket))
    }

    private suspend fun MockRequestHandleScope.refresh(request: HttpRequestData): HttpResponseData {
        refreshesSent++
        val token = KvizicJson.decodeFromString<RefreshRequest>(request.body.toByteArray().decodeToString())
        val refusal = refuseRefreshesWith
        // Rotation: a refresh token works once.
        val player = if (refusal == null) liveRefreshTokens.remove(token.refreshToken) else null
        return when {
            refusal != null -> respondErrorDto(refusal.first, refusal.second)
            player == null -> respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.INVALID_REFRESH_TOKEN)
            else -> issueSession(player)
        }
    }

    /**
     * A Play Games sign-in, as the server's: a bearer it does not know is 401 before the code is spent; a
     * code Google refuses is 422; a Play Games player linked already signs in as its player, and one linked
     * to nobody is linked to the bearer's player, or to one minted for it. A new session either way.
     */
    private suspend fun MockRequestHandleScope.playGames(request: HttpRequestData): HttpResponseData {
        val authorization = request.headers[HttpHeaders.Authorization]
        val code = KvizicJson.decodeFromString<PlayGamesSignInRequest>(request.body.toByteArray().decodeToString())
        playGamesSentAs += authorization to code.serverAuthCode
        val caller = authorization.player()
        val refusal = refusePlayGamesWith
        if (refusal != null) return respondErrorDto(refusal.first, refusal.second)
        if (caller != null && caller !in players) {
            return respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
        }
        val gamesPlayer =
            playGamesCodes.remove(code.serverAuthCode)
                ?: return respondErrorDto(HttpStatusCode.UnprocessableEntity, ErrorCode.PLAY_GAMES_CODE_REFUSED)
        whilePlayGamesExchanges()
        val player =
            playGamesLinks.getOrPut(gamesPlayer) {
                caller?.takeUnless { it in playGamesLinks.values } ?: "games-$gamesPlayer"
            }
        return issueSession(player)
    }

    private suspend fun MockRequestHandleScope.avatar(request: HttpRequestData): HttpResponseData {
        val authorization = request.headers[HttpHeaders.Authorization]
        val picked = KvizicJson.decodeFromString<SetAvatarRequest>(request.body.toByteArray().decodeToString())
        avatarsSentAs += authorization to picked.avatarId
        val player = authorization.player()
        return when {
            player == null || player !in players -> {
                respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
            }

            picked.avatarId !in Avatars.ALL -> {
                respondErrorDto(HttpStatusCode.BadRequest, ErrorCode.INVALID_AVATAR)
            }

            else -> {
                avatars[player] = picked.avatarId
                respondJson(KvizicJson.encodeToString(profileOf(player)))
            }
        }
    }

    private suspend fun MockRequestHandleScope.report(request: HttpRequestData): HttpResponseData {
        val authorization = request.headers[HttpHeaders.Authorization]
        val report = KvizicJson.decodeFromString<ReportQuestionRequest>(request.body.toByteArray().decodeToString())
        reportsSentAs += authorization to report
        val player = authorization.player()
        return when {
            player == null || player !in players -> respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
            report.questionId !in reportable -> respondErrorDto(HttpStatusCode.NotFound, ErrorCode.QUESTION_NOT_FOUND)
            else -> respond("", HttpStatusCode.NoContent)
        }
    }

    private fun MockRequestHandleScope.delete(request: HttpRequestData): HttpResponseData {
        val authorization = request.headers[HttpHeaders.Authorization]
        deletionsSentAs += authorization
        val player = authorization.player()
        val refusal = refuseDeletionsWith
        return when {
            refusal != null -> {
                respondErrorDto(refusal.first, refusal.second)
            }

            player == null || player !in players -> {
                respondErrorDto(HttpStatusCode.Unauthorized, ErrorCode.UNAUTHORIZED)
            }

            else -> {
                // Gone for good, every session with it: a refresh of theirs is refused from now on. Their Play
                // Games link goes too, as the server's foreign key cascades it.
                players -= player
                liveRefreshTokens.values.removeAll { it == player }
                playGamesLinks.values.removeAll { it == player }
                respond("", HttpStatusCode.NoContent)
            }
        }
    }

    /** What `GET /v1/me` answers [player]: a Play Games name once linked, a nickname otherwise. */
    private fun profileOf(player: String): ProfileDto {
        val linked = player in playGamesLinks.values
        return ProfileDto(
            playerId = player,
            displayName = if (linked) "Ана" else "Брзи Јеж",
            nameSource = if (linked) NameSource.PLAY_GAMES else NameSource.GENERATED,
            avatarId = avatars[player] ?: "hedgehog",
            playGamesLinked = linked,
            stats = STATS,
        )
    }

    private fun MockRequestHandleScope.issueSession(playerId: String): HttpResponseData {
        players += playerId
        val refreshToken = "refresh-$playerId-${rotations++}"
        liveRefreshTokens[refreshToken] = playerId
        val session =
            SessionDto(
                playerId = playerId,
                accessToken = "access-$playerId",
                refreshToken = refreshToken,
                accessTokenExpiresInSeconds = 900,
            )
        return respondJson(KvizicJson.encodeToString(session))
    }

    /** The player an `Authorization` header names, as every session here is issued. */
    private fun String?.player(): String? = this?.removePrefix("Bearer access-")

    companion object {
        /** The first topics, in the server's order. */
        val TOPICS =
            TopicListDto(
                listOf(
                    TopicDto(id = "GEOGRAPHY", nameSr = "Географија", nameEn = "Geography", questionCount = 12),
                    TopicDto(id = "HISTORY", nameSr = "Историја", nameEn = "History", questionCount = 9),
                    TopicDto(id = "MUSIC", nameSr = "Музика", nameEn = "Music", questionCount = 0),
                ),
            )

        /** What every profile's stats say. */
        val STATS = PlayerStatsDto(gamesPlayed = 7, gamesWon = 2, answersGiven = 70, answersCorrect = 41)
    }
}
