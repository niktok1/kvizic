package io.ntole.kvizic.server.admin

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.player.AccountSort
import io.ntole.kvizic.core.player.AdminAccountDetailDto
import io.ntole.kvizic.core.player.AdminAccountDto
import io.ntole.kvizic.core.player.AdminAccountPageDto
import io.ntole.kvizic.core.player.AdminGameDto
import io.ntole.kvizic.core.player.AdminTopicStatDto
import io.ntole.kvizic.core.player.PlayerStatsDto
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.Identities
import io.ntole.kvizic.server.db.MatchPlayers
import io.ntole.kvizic.server.db.Matches
import io.ntole.kvizic.server.db.PlayerTopicStats
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.player.Levels
import io.ntole.kvizic.server.plugins.ApiFailure
import io.ntole.kvizic.server.plugins.pageLimit
import io.ntole.kvizic.server.plugins.requireValidId
import org.jetbrains.exposed.v1.core.Coalesce
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

/**
 * The moderator's view of the players, inside `adminRoutes`: a page of every account, searched by name or the
 * start of an id and ordered four ways, and one account with its topics and last games. Read only; a deletion
 * is `accountDeletionRoutes`. The list pages by offset: the accounts are few, and an order by a count has
 * no key to continue from.
 */
fun Route.accountAdminRoutes(
    db: Db,
    adminToken: AdminToken,
) {
    get(KvizicApi.Paths.ADMIN_ACCOUNTS) {
        call.requireAdmin(adminToken)
        val parameters = call.request.queryParameters
        val sort =
            parameters[KvizicApi.Query.SORT]?.let { name ->
                AccountSort.entries.firstOrNull { it.name == name } ?: throw ApiFailure.validation("no sort $name")
            } ?: AccountSort.RECENT
        val offset =
            parameters[KvizicApi.Query.CURSOR]?.let { raw ->
                raw.toIntOrNull()?.takeIf { it >= 0 } ?: throw ApiFailure.validation("no such cursor")
            } ?: 0
        val search = parameters[KvizicApi.Query.SEARCH]?.take(MAX_SEARCH_LENGTH)
        val limit = parameters.pageLimit()
        call.respond(db.query { AccountAdmin.page(sort, search, offset, limit) })
    }

    get(KvizicApi.Paths.ADMIN_ACCOUNT) {
        call.requireAdmin(adminToken)
        val id = call.parameters["id"].orEmpty().trim()
        requireValidId("id", id)
        call.respond(db.query { AccountAdmin.detail(id) ?: throw ApiFailure.playerNotFound() })
    }
}

/** The reads behind [accountAdminRoutes]. Each must run inside a transaction. */
internal object AccountAdmin {
    /** How many of a player's last games the detail lists. */
    const val RECENT_GAMES: Int = 20

    fun page(
        sort: AccountSort,
        search: String?,
        offset: Int,
        limit: Int,
    ): AdminAccountPageDto {
        var condition: Op<Boolean> = Op.TRUE
        search?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { term ->
            val pattern = LikePattern("%", escapeChar = '\\') + LikePattern.ofLiteral(term) + "%"
            val idPattern = LikePattern.ofLiteral(term) + "%"
            condition = (Players.displayName.lowerCase() like pattern) or (Players.id like idPattern)
        }
        val joined = Players.join(Profiles, JoinType.INNER, Players.id, Profiles.playerId)
        val total =
            joined
                .select(Players.id)
                .where { condition }
                .count()
                .toInt()
        val order =
            when (sort) {
                AccountSort.RECENT -> Coalesce(Players.lastSeenAt, Players.createdAt) to SortOrder.DESC
                AccountSort.CREATED -> Players.createdAt to SortOrder.DESC
                AccountSort.LEVEL -> Profiles.xp to SortOrder.DESC
                AccountSort.GAMES -> Profiles.gamesPlayed to SortOrder.DESC
            }
        val rows =
            joined
                .selectAll()
                .where { condition }
                .orderBy(order, Players.id to SortOrder.ASC)
                .limit(limit)
                .offset(offset.toLong())
                .toList()
        val linked = linkedToPlayGames(rows.map { it[Players.id] })
        val next = (offset + rows.size).takeIf { rows.size == limit && it < total }?.toString()
        return AdminAccountPageDto(rows.map { it.toAccount(it[Players.id] in linked) }, next, total)
    }

    fun detail(id: String): AdminAccountDetailDto? {
        val row =
            Players
                .join(Profiles, JoinType.INNER, Players.id, Profiles.playerId)
                .selectAll()
                .where { Players.id eq id }
                .singleOrNull()
                ?: return null
        val topics =
            PlayerTopicStats
                .select(PlayerTopicStats.topicId, PlayerTopicStats.answered, PlayerTopicStats.correct)
                .where { PlayerTopicStats.playerId eq id }
                .map {
                    AdminTopicStatDto(
                        it[PlayerTopicStats.topicId],
                        it[PlayerTopicStats.answered],
                        it[PlayerTopicStats.correct],
                    )
                }.sortedWith(compareByDescending<AdminTopicStatDto> { it.answered }.thenBy { it.topicId })
        val games =
            MatchPlayers
                .join(Matches, JoinType.INNER, MatchPlayers.matchId, Matches.id)
                .select(
                    Matches.endedAt,
                    Matches.kind,
                    Matches.participants,
                    MatchPlayers.standing,
                    MatchPlayers.score,
                    MatchPlayers.correct,
                    MatchPlayers.answered,
                    MatchPlayers.finished,
                ).where { MatchPlayers.playerId eq id }
                .orderBy(Matches.endedAt to SortOrder.DESC, Matches.id to SortOrder.DESC)
                .limit(RECENT_GAMES)
                .map {
                    AdminGameDto(
                        endedAt = it[Matches.endedAt],
                        solo = it[Matches.kind] == LobbyKind.SOLO,
                        participants = it[Matches.participants],
                        standing = it[MatchPlayers.standing],
                        score = it[MatchPlayers.score],
                        correct = it[MatchPlayers.correct],
                        answered = it[MatchPlayers.answered],
                        finished = it[MatchPlayers.finished],
                    )
                }
        return AdminAccountDetailDto(row.toAccount(id in linkedToPlayGames(listOf(id))), topics, games)
    }

    private fun linkedToPlayGames(ids: List<String>): Set<String> =
        if (ids.isEmpty()) {
            emptySet()
        } else {
            Identities
                .select(Identities.playerId)
                .where { Identities.playerId inList ids }
                .map { it[Identities.playerId] }
                .toSet()
        }

    private fun ResultRow.toAccount(playGamesLinked: Boolean): AdminAccountDto {
        val xp = this[Profiles.xp]
        return AdminAccountDto(
            playerId = this[Players.id],
            displayName = this[Players.displayName],
            nameSource = this[Players.nameSource],
            avatarId = this[Profiles.avatarId],
            playGamesLinked = playGamesLinked,
            level = Levels.of(xp).number,
            xp = xp,
            stats =
                PlayerStatsDto(
                    gamesPlayed = this[Profiles.gamesPlayed],
                    gamesWon = this[Profiles.gamesWon],
                    answersGiven = this[Profiles.answersGiven],
                    answersCorrect = this[Profiles.answersCorrect],
                    soloRuns = this[Profiles.soloRuns],
                    soloBestScore = this[Profiles.soloBestScore],
                    soloBestEasyScore = this[Profiles.soloBestEasyScore],
                    soloBestHardScore = this[Profiles.soloBestHardScore],
                ),
            createdAt = this[Players.createdAt],
            lastSeenAt = this[Players.lastSeenAt],
        )
    }
}

private const val MAX_SEARCH_LENGTH = 100
