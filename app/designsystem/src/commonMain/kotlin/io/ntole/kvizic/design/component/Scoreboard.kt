package io.ntole.kvizic.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import io.ntole.kvizic.design.icon.KvizicIcons
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
    /** How many they got right, beside a check mark, or none to show. */
    val right: RightCount? = null,
    /** For one who left before the end, what a screen reader hears of the door their greyed line shows. */
    val left: String? = null,
)

/** How many answers a player got right, and the words a screen reader hears for the check mark and the count. */
@Immutable
data class RightCount(
    val count: Int,
    val said: String,
)

/** A check mark and how many a player got right, in the gain colour, read to a screen reader as [right] says. */
@Composable
internal fun RightMark(
    right: RightCount,
    modifier: Modifier = Modifier,
) {
    val space = KvizicTheme.space
    val gain = KvizicTheme.colors.gain
    Row(
        modifier.clearAndSetSemantics { contentDescription = right.said },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.xxs),
    ) {
        KvizicIcon(KvizicIcons.Check, contentDescription = null, tint = gain, size = space.icon.small)
        KvizicText(right.count.toString(), style = KvizicTheme.type.chip, color = gain, maxLines = 1)
    }
}

/**
 * The standings on a board: place, avatar, name, what the last question gave, and the total on flaps.
 * [burst], when given, stands over each line's avatar by its [ScoreRow.id], a player's reaction bursting there.
 */
@Composable
fun Scoreboard(
    rows: List<ScoreRow>,
    modifier: Modifier = Modifier,
    burst: (@Composable (id: String) -> Unit)? = null,
) {
    val space = KvizicTheme.space
    Panel(modifier, padding = space.md) {
        Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
            rows.forEach { row -> ScoreLine(row, burst = burst) }
        }
    }
}

/** [content], with [burst] for [id] over its middle, free past its edges and taking no room of its own. */
@Composable
internal fun BurstOver(
    id: String,
    burst: (@Composable (id: String) -> Unit)?,
    content: @Composable () -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        content()
        if (burst != null) Box(Modifier.matchParentSize().wrapContentSize(unbounded = true)) { burst(id) }
    }
}

/** Points as a scoreboard says them: a plus before a gain, a true minus before a loss. */
fun signed(points: Int): String =
    when {
        points > 0 -> "+$points"
        points < 0 -> "−${-points}"
        else -> "0"
    }
