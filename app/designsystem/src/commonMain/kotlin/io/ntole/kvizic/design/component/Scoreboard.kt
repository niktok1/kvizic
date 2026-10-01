package io.ntole.kvizic.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.ntole.kvizic.design.skin.KvizicTheme

/** A player's line on a scoreboard. */
@Immutable
data class ScoreRow(
    val place: Int,
    val name: String,
    val avatarId: String,
    val seat: Int,
    val total: Int,
    /** What the last question won or lost them, or none to show. */
    val delta: Int? = null,
    val host: Boolean = false,
    /** The player's own line, set in the accent. */
    val own: Boolean = false,
)

/** The standings on a board: place, avatar, name, what the last question gave, and the total on flaps. */
@Composable
fun Scoreboard(
    rows: List<ScoreRow>,
    modifier: Modifier = Modifier,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Panel(modifier, padding = space.md) {
        Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
            rows.forEach { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                ) {
                    KvizicText(
                        "${row.place}.",
                        Modifier.width(space.lg),
                        style = type.name,
                        color = colors.onRaisedMuted,
                        maxLines = 1,
                    )
                    Avatar(row.avatarId, row.seat, size = AvatarSize.XS, host = row.host)
                    KvizicText(
                        row.name,
                        Modifier.weight(1f),
                        style = type.name,
                        color = if (row.own) colors.onPageAccent else colors.onRaised,
                        maxLines = 1,
                    )
                    row.delta?.let { delta -> Chip(signed(delta), tone = toneOf(delta)) }
                    FlipNumber(row.total, size = FlapSize.SMALL)
                }
            }
        }
    }
}

/** Points as a scoreboard says them: a plus before a gain, a true minus before a loss. */
fun signed(points: Int): String =
    when {
        points > 0 -> "+$points"
        points < 0 -> "−${-points}"
        else -> "0"
    }

private fun toneOf(delta: Int): ChipTone =
    when {
        delta > 0 -> ChipTone.GAIN
        delta < 0 -> ChipTone.LOSS
        else -> ChipTone.NEUTRAL
    }
