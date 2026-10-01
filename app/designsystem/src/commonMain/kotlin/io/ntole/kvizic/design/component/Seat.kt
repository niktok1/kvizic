package io.ntole.kvizic.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme

/** Who sits in a lobby's seat, as the seat shows them. */
@Immutable
data class SeatOccupant(
    val name: String,
    val avatarId: String,
    /** Their seat, which gives their colour. */
    val seat: Int,
    /** A word under the name: the host's title, or that the seat is the player's own. */
    val badge: String? = null,
    val host: Boolean = false,
    /** Not in the room now: their connection dropped, or they still look at the last game's results. */
    val away: Boolean = false,
    /**
     * The votes to put them out of the room, as the seat shows them in its badge's place ("2/3"), and in
     * words for a screen reader: none while nobody votes them out.
     */
    val votes: String? = null,
    val votesDescription: String? = null,
)

/**
 * A lobby's seat: its [occupant]'s avatar, name and [SeatOccupant.badge], greyed while they are away, or,
 * empty, [emptyLabel] on an outlined well. Votes to put the occupant out show in the badge's place, a chip in
 * the loss tone. [onClick] makes it a button, a host's way to a member;
 * [contentDescription] says the seat to a screen reader.
 */
@Composable
fun Seat(
    occupant: SeatOccupant?,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val tap = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    val described =
        if (contentDescription != null) {
            Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
        } else {
            Modifier.semantics(mergeDescendants = true) {}
        }
    Panel(
        modifier = modifier.height(space.seat.height).then(described).then(tap),
        kind = if (occupant == null) PanelKind.EMPTY else PanelKind.PLAIN,
        padding = space.xs,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (occupant == null) {
                KvizicText(emptyLabel, style = type.caption, color = colors.onPageMuted, maxLines = 1)
            } else {
                Avatar(
                    occupant.avatarId,
                    occupant.seat,
                    size = AvatarSize.MD,
                    host = occupant.host,
                    dimmed = occupant.away,
                )
                Spacer(Modifier.height(space.xs))
                KvizicText(occupant.name, style = type.caption, maxLines = 1, textAlign = TextAlign.Center)
                val votes = occupant.votes
                if (votes != null) {
                    Box(
                        Modifier.clearAndSetSemantics {
                            occupant.votesDescription?.let { this.contentDescription = it }
                        },
                    ) {
                        Chip(votes, icon = KvizicIcons.Leave, tone = ChipTone.LOSS)
                    }
                } else {
                    occupant.badge?.let { badge ->
                        KvizicText(badge, style = type.caption, color = colors.onPageAccent, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * A lobby's seats, in rows of the skin's columns, an empty one where [seats] holds none; [onSeatClick]
 * makes each occupied seat a button, by its place in [seats]. [describe] names each to a screen reader.
 */
@Composable
fun SeatGrid(
    seats: List<SeatOccupant?>,
    emptyLabel: String,
    modifier: Modifier = Modifier,
    onSeatClick: ((Int) -> Unit)? = null,
    describe: (SeatOccupant?) -> String? = { null },
) {
    val space = KvizicTheme.space
    val columns = space.seat.columns
    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.sm)) {
        seats.withIndex().chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                row.forEach { (i, occupant) ->
                    Seat(
                        occupant,
                        emptyLabel,
                        Modifier.weight(1f),
                        onClick = if (occupant != null && onSeatClick != null) ({ onSeatClick(i) }) else null,
                        contentDescription = describe(occupant),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
