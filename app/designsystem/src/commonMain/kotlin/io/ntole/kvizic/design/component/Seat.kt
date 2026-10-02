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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.cued

/** Who sits in a lobby's seat, as the seat shows them. */
@Immutable
data class SeatOccupant(
    val name: String,
    val avatarId: String,
    /** Their seat, which gives their colour. */
    val seat: Int,
    /** Their level, on their avatar's badge, or none to show. */
    val level: Int? = null,
    /**
     * What the seat says of them in a word, to a screen reader alone, since the seat shows it otherwise:
     * the host's title (the microphone on the avatar), that the seat is the player's own (lit), or that
     * they still look at the last game's results ([status]).
     */
    val badge: String? = null,
    val host: Boolean = false,
    /** The player's own seat, lit among the others. */
    val own: Boolean = false,
    /** An icon under the name, in a word's place: an hourglass for a member still on the last results. */
    val status: ImageVector? = null,
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
 * A lobby's seat: its [occupant]'s avatar and name, greyed while they are away, their
 * [SeatOccupant.status] under it, and lit when it is the player's own; or, empty, an outlined well with
 * the shape of a person in it, which [emptyDescription] names to a screen reader. Votes to put the occupant
 * out show under the name, a chip in the loss tone. [onClick] makes it a button, a host's way to a member;
 * [contentDescription] says the seat to a screen reader, after the occupant's [SeatOccupant.badge].
 */
@Composable
fun Seat(
    occupant: SeatOccupant?,
    emptyDescription: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val tap =
        if (onClick !=
            null
        ) {
            Modifier.clickable(role = Role.Button, onClick = cued(Cue.TAP_SOFT, onClick))
        } else {
            Modifier
        }
    val said =
        when (occupant) {
            null -> emptyDescription
            else -> listOfNotNull(occupant.badge, contentDescription).joinToString(", ").ifEmpty { null }
        }
    val described =
        if (said != null) {
            Modifier.semantics(mergeDescendants = true) { this.contentDescription = said }
        } else {
            Modifier.semantics(mergeDescendants = true) {}
        }
    Panel(
        modifier = modifier.height(space.seat.height).then(described).then(tap),
        kind =
            when {
                occupant == null -> PanelKind.EMPTY
                occupant.own -> PanelKind.OWN
                else -> PanelKind.PLAIN
            },
        padding = space.xs,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (occupant == null) {
                KvizicIcon(KvizicIcons.Person, contentDescription = null, size = space.icon.large)
            } else {
                Avatar(
                    occupant.avatarId,
                    occupant.seat,
                    size = AvatarSize.MD,
                    host = occupant.host,
                    level = occupant.level,
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
                        Chip(votes, icon = KvizicIcons.Boot, tone = ChipTone.LOSS)
                    }
                } else {
                    occupant.status?.let { status ->
                        KvizicIcon(
                            status,
                            contentDescription = null,
                            tint = colors.onPageAccent,
                            size = space.icon.small,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A lobby's seats, in rows of the skin's columns, an empty one, [emptyDescription] to a screen reader, where
 * [seats] holds none; [onSeatClick] makes each occupied seat a button, by its place in [seats]. [describe]
 * names each to a screen reader.
 */
@Composable
fun SeatGrid(
    seats: List<SeatOccupant?>,
    emptyDescription: String,
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
                        emptyDescription,
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
