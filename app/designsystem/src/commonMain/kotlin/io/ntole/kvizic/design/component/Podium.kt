package io.ntole.kvizic.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme

/** One of a game's top three, as the podium shows them. */
@Immutable
data class PodiumPlace(
    val name: String,
    val avatarId: String,
    val seat: Int,
    val score: Int,
    /** Their level, on their avatar's badge, or none to show. */
    val level: Int? = null,
)

/**
 * A game's top three on their steps, the winner in the middle and highest with a crown, second to the
 * left and third to the right, each with their avatar, name and score. [places] is in the order they
 * finished, one to three of them; a screen reader reads each place whole, in that order.
 */
@Composable
fun Podium(
    places: List<PodiumPlace>,
    modifier: Modifier = Modifier,
) {
    require(places.size in 1..PODIUM_PLACES) { "a podium has one to $PODIUM_PLACES places, not ${places.size}" }
    val skin = KvizicTheme.skin
    val space = skin.space
    val type = KvizicTheme.type
    val part = skin.parts.podium
    Row(
        modifier = modifier.semantics { isTraversalGroup = true },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(space.podium.stepGap),
    ) {
        STAGE_ORDER.forEach { place ->
            val standing = places.getOrNull(place - 1)
            Column(
                // Read first to third, not left to right as the steps stand.
                modifier = Modifier.weight(1f).semantics(mergeDescendants = true) { traversalIndex = place.toFloat() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (standing != null) {
                    val crown = skin.colors.onPageAccent
                    if (place == 1) KvizicIcon(KvizicIcons.Crown, contentDescription = null, tint = crown)
                    Avatar(
                        avatarId = standing.avatarId,
                        seat = standing.seat,
                        level = standing.level,
                        size = if (place == 1) AvatarSize.XL else AvatarSize.LG,
                    )
                    Spacer(Modifier.height(space.xs))
                    KvizicText(standing.name, style = type.name, maxLines = 1, textAlign = TextAlign.Center)
                    FlipNumber(standing.score, size = FlapSize.SMALL)
                    Spacer(Modifier.height(space.sm))
                }
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(
                                when (place) {
                                    1 -> space.podium.first
                                    2 -> space.podium.second
                                    else -> space.podium.third
                                },
                            ).raised(part.surface(place), skin.depth, skin.motion),
                    contentAlignment = Alignment.Center,
                ) {
                    KvizicText(place.toString(), style = type.headline, color = part.content(place))
                }
            }
        }
    }
}

private const val PODIUM_PLACES = 3

/** The places as they stand on the stage, left to right. */
private val STAGE_ORDER = listOf(2, 1, 3)
