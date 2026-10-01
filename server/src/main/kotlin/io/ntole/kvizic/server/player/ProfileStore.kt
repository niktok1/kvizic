package io.ntole.kvizic.server.player

import io.ntole.kvizic.core.player.PlayerStatsDto
import io.ntole.kvizic.core.player.ProfileDto
import io.ntole.kvizic.server.auth.IdentityStore
import io.ntole.kvizic.server.db.PlayerTopicStats
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.Profiles
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.select

object ProfileStore {
    /** How many answers in a topic before it can be a player's best. */
    const val MIN_ANSWERS_FOR_BEST_TOPIC: Int = 10

    /** [playerId]'s profile and stats, or null when there is no such player. Must run inside a transaction. */
    fun of(playerId: String): ProfileDto? {
        val row =
            Players
                .join(Profiles, JoinType.INNER, Players.id, Profiles.playerId)
                .selectAll(playerId)
                ?: return null
        val bestTopic =
            PlayerTopicStats
                .select(PlayerTopicStats.topicId, PlayerTopicStats.answered, PlayerTopicStats.correct)
                .where {
                    (PlayerTopicStats.playerId eq playerId) and
                        (PlayerTopicStats.answered greaterEq MIN_ANSWERS_FOR_BEST_TOPIC)
                }.maxWithOrNull(
                    compareBy<org.jetbrains.exposed.v1.core.ResultRow> {
                        it[PlayerTopicStats.correct].toDouble() / it[PlayerTopicStats.answered]
                    }.thenBy { it[PlayerTopicStats.answered] }
                        .thenByDescending { it[PlayerTopicStats.topicId] },
                )?.get(PlayerTopicStats.topicId)

        return ProfileDto(
            playerId = playerId,
            displayName = row[Players.displayName],
            nameSource = row[Players.nameSource],
            avatarId = row[Profiles.avatarId],
            playGamesLinked = IdentityStore.isLinked(playerId),
            stats =
                PlayerStatsDto(
                    gamesPlayed = row[Profiles.gamesPlayed],
                    gamesWon = row[Profiles.gamesWon],
                    answersGiven = row[Profiles.answersGiven],
                    answersCorrect = row[Profiles.answersCorrect],
                    bestTopicId = bestTopic,
                    soloRuns = row[Profiles.soloRuns],
                    soloBestScore = row[Profiles.soloBestScore],
                    soloBestEasyScore = row[Profiles.soloBestEasyScore],
                    soloBestHardScore = row[Profiles.soloBestHardScore],
                ),
        )
    }

    private fun org.jetbrains.exposed.v1.core.Join.selectAll(playerId: String) =
        select(
            Players.displayName,
            Players.nameSource,
            Profiles.avatarId,
            Profiles.gamesPlayed,
            Profiles.gamesWon,
            Profiles.answersGiven,
            Profiles.answersCorrect,
            Profiles.soloRuns,
            Profiles.soloBestScore,
            Profiles.soloBestEasyScore,
            Profiles.soloBestHardScore,
        ).where { Players.id eq playerId }
            .singleOrNull()
}
