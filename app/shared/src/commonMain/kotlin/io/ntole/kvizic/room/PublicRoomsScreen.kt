package io.ntole.kvizic.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.core.domain.error.DomainError
import io.ntole.kvizic.core.domain.lobby.PublicLobbies
import io.ntole.kvizic.core.domain.lobby.PublicLobby
import io.ntole.kvizic.design.component.Avatar
import io.ntole.kvizic.design.component.AvatarSize
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.home.Notice
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.failureText
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.loading.LoadingSpinner

/**
 * The open public rooms, read again every few seconds while shown ([lobbies], none before the first read):
 * each its host, how full it is, its settings and whether a game is under way; a tap joins it ([onJoin], by
 * its code). With none open, the way to Quick play and to a room of the player's own.
 */
@Composable
fun PublicRoomsScreen(
    lobbies: PublicLobbies?,
    failure: DomainError?,
    entry: Entry,
    onJoin: (String) -> Unit,
    onQuickPlay: () -> Unit,
    onCreate: () -> Unit,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val words = strings.game
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val taking = entry is Entry.Taking
    Page {
        if (entry is Entry.Failed && entry.way == EntryWay.JOIN) {
            Notice(strings.entryFailureText(entry.error, entry.retryAfter), onDismiss = onDismissFailure)
            Spacer(Modifier.height(space.md))
        }
        when {
            lobbies == null && failure != null -> {
                KvizicText(
                    strings.failureText(failure),
                    Modifier.fillMaxWidth(),
                    color = colors.loss,
                    textAlign = TextAlign.Center,
                )
            }

            lobbies == null -> {
                LoadingSpinner(name = strings.loading, modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            lobbies.lobbies.isEmpty() -> {
                Spacer(Modifier.weight(1f))
                KvizicText(
                    words.noPublicRooms,
                    Modifier.fillMaxWidth(),
                    style = type.body,
                    color = colors.onPageMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(space.lg))
                StageButton(
                    words.quickPlay,
                    onClick = tapped("public_rooms.quick_play", onClick = onQuickPlay),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !taking,
                )
                Spacer(Modifier.height(space.sm))
                StageButton(
                    words.createRoom,
                    onClick = tapped("public_rooms.create_room", onClick = onCreate),
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.SECONDARY,
                    enabled = !taking,
                )
                Spacer(Modifier.weight(1f))
            }

            else -> {
                KvizicText(
                    words.presence.fill(lobbies.online, lobbies.searching),
                    Modifier.fillMaxWidth(),
                    style = type.caption,
                    color = colors.onPageMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(space.md))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.sm)) {
                    items(lobbies.lobbies, key = { it.code }) { lobby ->
                        PublicRoom(lobby, enabled = !taking, onJoin = onJoin)
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicRoom(
    lobby: PublicLobby,
    enabled: Boolean,
    onJoin: (String) -> Unit,
) {
    val words = LocalStrings.current.game
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val full = lobby.players >= lobby.maxPlayers
    val tap = tapped("public_rooms.room") { onJoin(lobby.code) }
    Panel(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription =
                    words.publicRoomOf.fill(shownIn(lobby.hostName, language))
            }.clickable(enabled = enabled && !full, role = Role.Button, onClick = tap),
        padding = space.md,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.md)) {
            Avatar(lobby.hostAvatar, seat = 0, size = AvatarSize.SM, host = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xxs)) {
                KvizicText(shownIn(lobby.hostName, language), style = type.name, maxLines = 1)
                KvizicText(
                    settingsLine(lobby),
                    style = type.caption,
                    color = colors.onRaisedMuted,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(space.xxs)) {
                Chip("${lobby.players} / ${lobby.maxPlayers}", tone = if (full) ChipTone.LOSS else ChipTone.NEUTRAL)
                if (lobby.inGame) KvizicText(words.inGame, style = type.caption, color = colors.onPageAccent)
            }
        }
    }
}

@Composable
private fun settingsLine(lobby: PublicLobby): String =
    settingsChips(lobby.settings).take(SETTINGS_SHOWN).joinToString(" · ")

/** How many of a room's settings its line names: the questions, their time and the topics. */
private const val SETTINGS_SHOWN = 3
