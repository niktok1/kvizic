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
    /** How many places the line rose since the last standings, a fall below 0: what [StandingsBoard] slides. */
    val moved: Int = 0,
    /** What tells the line from the others' as it moves: the player's id. */
    val id: String = name,
)

/** The standings on a board: place, avatar, name, what the last question gave, and the total on flaps. */
@Composable
fun Scoreboard(
    rows: List<ScoreRow>,
    modifier: Modifier = Modifier,
) {
    val space = KvizicTheme.space
    Panel(modifier, padding = space.md) {
        Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
            rows.forEach { row -> ScoreLine(row) }
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
