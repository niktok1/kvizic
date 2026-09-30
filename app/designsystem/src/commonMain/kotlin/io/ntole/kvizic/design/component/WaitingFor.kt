package io.ntole.kvizic.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme

/**
 * Who a question still waits for: an hourglass, then the avatar of each member yet to answer, the player
 * among them, in the order given. Each leaves as they answer, and once none is left the question ends
 * early. As tall as an avatar however many are left, none included, so nothing above or below it moves as
 * it empties. [contentDescription] names them to a screen reader, in the screen's words: neither the
 * hourglass nor an avatar says anything itself.
 */
@Composable
fun WaitingFor(
    avatars: List<AvatarChip>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val space = KvizicTheme.space
    val described =
        if (contentDescription != null) {
            Modifier.semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
        } else {
            Modifier
        }
    Row(
        modifier = modifier.heightIn(min = space.avatar.xs).then(described),
        horizontalArrangement = Arrangement.spacedBy(space.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatars.isNotEmpty()) {
            KvizicIcon(KvizicIcons.Hourglass, contentDescription = null, size = space.icon.small)
            Spacer(Modifier.width(space.xs))
            avatars.forEach { Avatar(it.avatarId, it.seat, size = AvatarSize.XS) }
        }
    }
}
