package io.ntole.kvizic.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import io.ntole.kvizic.design.avatar.AvatarArt
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.SkinSpace

/**
 * A member's avatar: the animal of [avatarId] (a silhouette for one this build has not drawn), framed in
 * the colour of their [seat], and, for the room's [host], the host's microphone on a badge. [order] marks
 * the member's place among the first to answer right, on a badge of its own. [dimmed] greys it out: a
 * member who has not answered yet. [contentDescription] names the member to a screen reader.
 */
@Composable
fun Avatar(
    avatarId: String,
    seat: Int,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.MD,
    host: Boolean = false,
    order: Int? = null,
    dimmed: Boolean = false,
    contentDescription: String? = null,
) {
    val skin = KvizicTheme.skin
    val part = skin.parts.avatar
    val whole = skin.space.sizeOf(size)
    val seatColor = skin.colors.seat(seat)
    val art = AvatarArt.of(avatarId, skin.avatarPalette)
    val described =
        if (contentDescription !=
            null
        ) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    Box(modifier.size(whole).then(described)) {
        Spacer(Modifier.matchParentSize().drawBehind { with(part) { drawUnder(seatColor, dimmed) } })
        Image(
            painter = rememberVectorPainter(art),
            contentDescription = null,
            modifier = Modifier.align(Alignment.Center).size(whole - part.artInset * 2).clip(skin.shapes.avatar),
            colorFilter = if (dimmed) Greyed else null,
            alpha = if (dimmed) DIMMED_ALPHA else 1f,
        )
        Spacer(Modifier.matchParentSize().drawBehind { with(part) { drawOver(seatColor, dimmed) } })
        if (host) {
            val badge = whole * skin.space.avatar.badgeFraction
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(badge)
                        .drawBehind { with(part) { drawHostBadge() } },
                contentAlignment = Alignment.Center,
            ) {
                KvizicIcon(KvizicIcons.Mic, contentDescription = null, tint = part.hostIcon, size = badge * BADGE_ICON)
            }
        }
        if (order != null) {
            // Never smaller than its number: the smallest avatars badge the order at a size it reads at.
            // At the top start, where the next avatar of a stack, laid over this one's end, leaves it clear.
            val badge = max(whole * skin.space.avatar.badgeFraction, skin.space.icon.small)
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = -badge / BADGE_OUT, y = -badge / BADGE_OUT)
                        .size(badge)
                        .drawBehind { with(part) { drawHostBadge() } },
                contentAlignment = Alignment.Center,
            ) {
                KvizicText(order.toString(), style = KvizicTheme.type.badge, color = part.hostIcon, maxLines = 1)
            }
        }
    }
}

/** An avatar to show in a row of them, by its id and its member's seat, and its place among the first right. */
@Immutable
data class AvatarChip(
    val avatarId: String,
    val seat: Int,
    val order: Int? = null,
)

/**
 * The most rows an [AvatarStack] may take where one is short of room: a tile with room under its letter gives
 * those who picked it two, so a crowd stands in two rows rather than closed up in one.
 */
internal val LocalAvatarRows = staticCompositionLocalOf { 1 }

/**
 * Several avatars, each over the one before by the skin's overlap, closer where their room is short: who
 * picked an answer. [contentDescription] says it to a screen reader in a word, since the avatars do not.
 *
 * Where its place lets it take two rows ([LocalAvatarRows]) and one would close up past halfway to the skin's
 * crowd, the first row fills to that and the rest stand in a second under it, so no one moves as more come; a
 * crowd of more than two such rows hold shares them alike. Its rows close up alike and stand at its end.
 */
@Composable
fun AvatarStack(
    avatars: List<AvatarChip>,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.XS,
    contentDescription: String? = null,
) {
    val space = KvizicTheme.space
    val step = space.sizeOf(size) * (1 - space.avatar.stackOverlap)
    val described =
        if (contentDescription !=
            null
        ) {
            Modifier.semantics { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    val closest = space.sizeOf(size) * (1 - space.avatar.crowdOverlap)
    val rowsAllowed = LocalAvatarRows.current
    Layout(
        content = { avatars.forEach { Avatar(it.avatarId, it.seat, size = size, order = it.order) } },
        modifier = modifier.then(described),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val face = placeables.lastOrNull()?.width ?: 0
        val stepMost = step.roundToPx()
        // How many stand in a row closed up no further than halfway to the skin's crowd.
        val comfortable = ((step + closest) / 2).roundToPx()
        val holds =
            if (constraints.hasBoundedWidth && comfortable > 0) {
                ((constraints.maxWidth - face) / comfortable + 1).coerceAtLeast(1)
            } else {
                Int.MAX_VALUE
            }
        val rows =
            when {
                rowsAllowed < 2 || placeables.size <= holds -> listOf(placeables)
                placeables.size <= holds * 2 -> listOf(placeables.take(holds), placeables.drop(holds))
                else -> placeables.chunked((placeables.size + 1) / 2)
            }

        // Short of room, a row closes up, each avatar over more of the one before, down to the skin's
        // closest: past that it runs over its room, as a stack of more than a room holds would.
        fun stepOf(row: List<Placeable>): Int {
            val fitting =
                if (row.size > 1 && constraints.hasBoundedWidth) {
                    (constraints.maxWidth - face) / (row.size - 1)
                } else {
                    Int.MAX_VALUE
                }
            return fitting.coerceIn(closest.roundToPx(), stepMost)
        }
        val stepPx = rows.minOf(::stepOf)
        val widths = rows.map { row -> if (row.isEmpty()) 0 else stepPx * (row.size - 1) + face }
        val width = widths.maxOrNull() ?: 0
        val rowHeight = placeables.maxOfOrNull { it.height } ?: 0
        val between = space.xxs.roundToPx()
        val height = if (placeables.isEmpty()) 0 else rows.size * rowHeight + (rows.size - 1) * between
        layout(width, height) {
            rows.forEachIndexed { r, row ->
                row.forEachIndexed { i, placeable ->
                    placeable.place(width - widths[r] + i * stepPx, r * (rowHeight + between))
                }
            }
        }
    }
}

/**
 * The least width, in pixels, an [AvatarStack] of [count] avatars of [size] closes up to short of room: a crowd,
 * each face over the one before by the skin's closest.
 */
internal fun Density.crowdWidth(
    space: SkinSpace,
    count: Int,
    size: AvatarSize = AvatarSize.XS,
): Int =
    if (count <= 0) {
        0
    } else {
        (space.sizeOf(size) * (1 - space.avatar.crowdOverlap)).roundToPx() * (count - 1) +
            space.sizeOf(size).roundToPx()
    }

/** The size of an avatar of [size]. */
internal fun SkinSpace.sizeOf(size: AvatarSize): Dp =
    when (size) {
        AvatarSize.XS -> avatar.xs
        AvatarSize.SM -> avatar.sm
        AvatarSize.MD -> avatar.md
        AvatarSize.LG -> avatar.lg
        AvatarSize.XL -> avatar.xl
    }

/** A member who has not answered yet: their avatar drained of colour and faded. */
private val Greyed = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private const val DIMMED_ALPHA = 0.55f

/** The microphone's share of its badge. */
private const val BADGE_ICON = 0.68f

/** How far an order's badge hangs past the avatar's corner, as a share of its own size. */
private const val BADGE_OUT = 4
