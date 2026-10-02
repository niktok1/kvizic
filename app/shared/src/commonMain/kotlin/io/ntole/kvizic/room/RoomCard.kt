package io.ntole.kvizic.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.core.domain.lobby.PublicLobby
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.design.component.Avatar
import io.ntole.kvizic.design.component.AvatarSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.fill

/**
 * A room as a player sees it before they take a seat in it, in the public list and in a join's preview: its
 * name, or its host's when it has none, with the host's under a name; every setting on its chip, so nobody
 * joins what they did not choose; how full it is and whether a game is under way. With an [onJoin] a tap on
 * the card joins it, by the room's code; a full room takes none.
 */
@Composable
internal fun RoomCard(
    lobby: PublicLobby,
    topics: List<Topic>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onJoin: ((String) -> Unit)? = null,
) {
    val words = LocalStrings.current.game
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val full = lobby.players >= lobby.maxPlayers
    val named = lobby.settings.name
    val title = shownIn(named ?: lobby.hostName, language)
    val tap = onJoin?.let { join -> tapped("public_rooms.room") { join(lobby.code) } }
    Panel(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = words.publicRoomOf.fill(title) }
            .then(
                if (tap !=
                    null
                ) {
                    Modifier.clickable(enabled = enabled && !full, role = Role.Button, onClick = tap)
                } else {
                    Modifier
                },
            ),
        padding = space.md,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.md)) {
            Avatar(lobby.hostAvatar, seat = 0, size = AvatarSize.SM, host = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xs)) {
                KvizicText(title, style = type.name, maxLines = 1)
                if (named != null) {
                    KvizicText(
                        shownIn(lobby.hostName, language),
                        style = type.caption,
                        color = colors.onRaisedMuted,
                        maxLines = 1,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                    verticalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    settingsChips(lobby.settings, topics).forEach { chip -> Chip(chip.text, icon = chip.icon) }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(space.xxs)) {
                Chip("${lobby.players} / ${lobby.maxPlayers}", tone = if (full) ChipTone.LOSS else ChipTone.NEUTRAL)
                if (lobby.inGame) KvizicText(words.inGame, style = type.caption, color = colors.onPageAccent)
            }
        }
    }
}
