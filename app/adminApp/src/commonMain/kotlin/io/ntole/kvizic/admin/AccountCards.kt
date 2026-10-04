package io.ntole.kvizic.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.ntole.kvizic.core.domain.moderation.AccountDetail
import io.ntole.kvizic.core.domain.moderation.AccountGame
import io.ntole.kvizic.core.domain.moderation.AccountSummary
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.component.PanelKind
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.skin.KvizicTheme

/** One player in the list: the name and level, how much they play, how right they are, and when they were seen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountRow(
    player: AccountSummary,
    now: Long,
    onOpen: () -> Unit,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Panel(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen), padding = space.md) {
        Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KvizicText(player.name, style = type.bodyStrong)
                Chip("Level ${player.level}", tone = ChipTone.ACCENT)
                if (player.playGamesLinked) Chip("Play Games")
            }
            KvizicText(statsOf(player), style = type.caption, color = colors.onRaisedMuted)
            KvizicText(
                "Seen ${ago(
                    player.lastActiveAt,
                    now,
                )}${if (player.lastSeenAt == null) " (created)" else ""} · ${player.id}",
                style = type.caption,
                color = colors.onRaisedMuted,
            )
        }
    }
}

/** An opened account: everything the list shows, when it was made, its topics and last games, and its deletion. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccountDetailCard(
    detail: AccountDetail,
    now: Long,
    state: ModerationState,
    actions: ModerationActions,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val player = detail.account
    Panel(Modifier.fillMaxWidth(), kind = PanelKind.SCREEN, padding = space.md) {
        Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KvizicText(player.name, style = type.question)
                Chip("Level ${player.level}", tone = ChipTone.ACCENT)
                if (player.playGamesLinked) Chip("Play Games") else Chip("Guest")
            }
            KvizicText(player.id, style = type.caption, color = colors.onRaisedMuted)
            KvizicText(
                "Created ${utc(player.createdAt)} UTC · seen " +
                    (player.lastSeenAt?.let { "${utc(it)} UTC, ${ago(it, now)}" } ?: "not since it was first noted"),
                style = type.caption,
                color = colors.onRaisedMuted,
            )
            KvizicText(statsOf(player), style = type.body)
            KvizicText(
                "${player.xp} experience · solo ${player.soloRuns} runs, best " +
                    "easy ${player.soloBest.easy ?: "–"}, medium ${player.soloBest.medium ?: "–"}, " +
                    "hard ${player.soloBest.hard ?: "–"}",
                style = type.caption,
                color = colors.onRaisedMuted,
            )
            if (detail.topics.isNotEmpty()) {
                KvizicText("Topics", style = type.label, color = colors.onRaisedMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                    verticalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    detail.topics.forEach {
                        Chip("${state.topicNames(listOf(it.topicId))} ${it.correct}/${it.answered}")
                    }
                }
            }
            if (detail.recentGames.isNotEmpty()) {
                KvizicText("Last games", style = type.label, color = colors.onRaisedMuted)
                detail.recentGames.forEach {
                    KvizicText(gameOf(it, now), style = type.caption, color = colors.onRaisedMuted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                StageButton(
                    "Delete account…",
                    onClick = actions::askToDelete,
                    kind = ButtonKind.SECONDARY,
                    size = ButtonSize.SMALL,
                    enabled = !state.busy,
                )
                StageButton("Close", onClick = actions::closeAccount, kind = ButtonKind.QUIET, size = ButtonSize.SMALL)
            }
        }
    }
}

/** "12 games, 4 won · 71% right of 140 answers": what they have played in rooms. */
internal fun statsOf(player: AccountSummary): String =
    "${player.gamesPlayed} games, ${player.gamesWon} won · " +
        (player.accuracyPercent?.let { "$it% right of ${player.answersGiven} answers" } ?: "no answers yet")

/** "3 d ago · 2nd of 4, 320 points, 7/10" or "… · solo, 280 points": one game as a line. */
internal fun gameOf(
    game: AccountGame,
    now: Long,
): String {
    val where =
        if (game.solo) {
            "solo"
        } else {
            "${place(game.standing)} of ${game.participants}${if (game.finished) "" else ", left early"}"
        }
    return "${ago(game.endedAt, now)} · $where, ${game.score} points, ${game.correct}/${game.answered} right"
}

private fun place(standing: Int): String {
    val suffix =
        when {
            standing % 100 in TEENS -> "th"
            standing % 10 == 1 -> "st"
            standing % 10 == 2 -> "nd"
            standing % 10 == 3 -> "rd"
            else -> "th"
        }
    return "$standing$suffix"
}

private val TEENS = 11..13
