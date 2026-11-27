package io.ntole.kvizic.server.achievement

import io.ntole.kvizic.core.player.AchievementDto
import io.ntole.kvizic.server.db.PlayerTitles
import io.ntole.kvizic.server.db.PlayerTopicStats
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.lobby.GameRecord
import io.ntole.kvizic.server.lobby.PlayerRecord
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.select

/**
 * The titles players earn by their [Achievements]: those their totals reach now, and those kept once reached,
 * so a goal that rises later takes none away. Every function must run inside a transaction.
 */
object AchievementStore {
    /** Every achievement on offer to [playerId] and every one they earned, as far as they have come. */
    fun of(playerId: String): List<AchievementDto> {
        val totals = totals(playerId)
        val kept = kept(playerId)
        return Achievements.ALL.mapNotNull { achievement ->
            val earned = achievement.titleId in kept || achievement.reachedBy(totals)
            if (achievement.retired && !earned) return@mapNotNull null
            AchievementDto(
                id = achievement.titleId,
                title = achievement.title,
                how = achievement.how,
                progress = if (earned) achievement.goal else minOf(achievement.count.of(totals), achievement.goal),
                goal = achievement.goal,
                earned = earned,
            )
        }
    }

    /** The titles [playerId] has earned. */
    fun earned(playerId: String): Set<String> = kept(playerId) + reached(totals(playerId))

    /** Keeps the titles [playerId]'s totals reach and none kept yet, at [now]. Call with the player's row locked. */
    fun keep(
        playerId: String,
        now: Long,
    ) {
        val fresh = reached(totals(playerId)) - kept(playerId)
        PlayerTitles.batchInsert(fresh, shouldReturnGeneratedValues = false) { titleId ->
            this[PlayerTitles.playerId] = playerId
            this[PlayerTitles.titleId] = titleId
            this[PlayerTitles.earnedAt] = now
        }
    }

    private fun reached(totals: Totals): Set<String> =
        Achievements.ALL
            .filter { it.reachedBy(totals) }
            .map { it.titleId }
            .toSet()

    private fun Achievement.reachedBy(totals: Totals) = !retired && count.of(totals) >= goal

    private fun kept(playerId: String): Set<String> =
        PlayerTitles
            .select(PlayerTitles.titleId)
            .where { PlayerTitles.playerId eq playerId }
            .map { it[PlayerTitles.titleId] }
            .toSet()

    private fun totals(playerId: String): Totals {
        val row =
            Profiles
                .select(Profiles.gamesWon, Profiles.answersCorrect, Profiles.perfectGames, Profiles.firstRights)
                .where { Profiles.playerId eq playerId }
                .singleOrNull()
                ?: return Totals()
        return Totals(
            wins = row[Profiles.gamesWon],
            rightAnswers = row[Profiles.answersCorrect],
            perfectGames = row[Profiles.perfectGames],
            firstRights = row[Profiles.firstRights],
            rightByTopic =
                PlayerTopicStats
                    .select(PlayerTopicStats.topicId, PlayerTopicStats.correct)
                    .where { PlayerTopicStats.playerId eq playerId }
                    .associate { it[PlayerTopicStats.topicId] to it[PlayerTopicStats.correct] },
        )
    }
}

/** Whether [player] answered every question of this game right, in one long enough to count. */
fun GameRecord.isPerfectFor(player: PlayerRecord): Boolean =
    player.finished && !endedEarly && questions.size >= Achievements.PERFECT_LEAST && player.correct == questions.size

/** How many of this game's questions [playerId] was the first to answer right; none in a game of one. */
fun GameRecord.firstRightsOf(playerId: String): Int =
    if (players.size < 2) {
        0
    } else {
        questions.count { question ->
            question.answers.entries
                .filter { it.value.correct }
                .minByOrNull { it.value.timeMs }
                ?.key == playerId
        }
    }
