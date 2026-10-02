package io.ntole.kvizic.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.core.domain.lobby.LobbyExit
import io.ntole.kvizic.design.component.Avatar
import io.ntole.kvizic.design.component.AvatarSize
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.component.PresenceStrip
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.StageIconButton
import io.ntole.kvizic.design.component.Wordmark
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.cued
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.failureText
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.room.Entry
import io.ntole.kvizic.room.EntryWay
import io.ntole.kvizic.room.entryFailureText
import io.ntole.kvizic.room.exitText
import io.ntole.kvizic.room.shown

/** Where the counts on Home come from: how many play and look for a game, and how many rooms are open. */
@Immutable
data class HomeCounts(
    val online: Int,
    val searching: Int,
    val publicRooms: Int,
)

/** What Home's buttons do. */
@Immutable
class HomeActions(
    val quickPlay: () -> Unit = {},
    val createRoom: () -> Unit = {},
    val joinByCode: () -> Unit = {},
    val publicRooms: () -> Unit = {},
    val solo: () -> Unit = {},
    val settings: () -> Unit = {},
    val profile: () -> Unit = {},
    val retry: () -> Unit = {},
    val dismissExit: () -> Unit = {},
    val dismissFailure: () -> Unit = {},
)

/**
 * Home, multiplayer first: the player, read from the server ([state]), the game's sign, Quick play the
 * largest, with how many play and look for a game ([counts]), a room of the player's own and a code under
 * it, the public rooms, and a small solo last. [entry] is a seat being taken from here, which turns the
 * buttons off, or why it could not be; [exit] why the last room let the player go, until it is taken down.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    counts: HomeCounts? = null,
    entry: Entry = Entry.None,
    exit: LobbyExit? = null,
) {
    val strings = LocalStrings.current
    val words = strings.game
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val taking = entry is Entry.Taking
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(
                    max = space.contentWidth,
                ).fillMaxSize()
                .padding(horizontal = space.screen, vertical = space.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.md),
            ) {
                val profile = state.profile
                if (profile != null) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clickable(
                                role = Role.Button,
                                onClick = cued(Cue.TAP_SOFT, tapped("home.profile", onClick = actions.profile)),
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space.md),
                    ) {
                        Avatar(state.changingAvatar ?: profile.avatarId, seat = 0, size = AvatarSize.SM)
                        Column(Modifier.weight(1f)) {
                            KvizicText(shown(profile.displayName), style = type.name, maxLines = 1)
                            KvizicText(
                                words.games.of(profile.stats.gamesPlayed, language) + " · " +
                                    words.wins.of(profile.stats.gamesWon, language),
                                style = type.caption,
                                color = colors.onPageMuted,
                                maxLines = 1,
                            )
                        }
                    }
                } else {
                    val failure = state.failure
                    Box(Modifier.weight(1f)) {
                        if (failure != null) {
                            StageButton(
                                strings.tryAgain,
                                onClick = tapped("home.try_again", onClick = actions.retry),
                                kind = ButtonKind.QUIET,
                                size = ButtonSize.SMALL,
                            )
                        }
                    }
                }
                StageIconButton(
                    KvizicIcons.Sliders,
                    contentDescription = strings.settingsScreen.title,
                    onClick = tapped("home.settings", onClick = actions.settings),
                    small = true,
                )
            }
            exit?.let { words.exitText(it) }?.let { text ->
                Spacer(Modifier.height(space.md))
                Notice(text, onDismiss = tapped("home.exit_ok", onClick = actions.dismissExit))
            }
            Spacer(Modifier.height(space.xl))
            Wordmark(strings.gameName, Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            if (entry is Entry.Failed) {
                Notice(
                    strings.entryFailureText(entry.error, entry.retryAfter),
                    onDismiss = tapped("home.failure_ok", onClick = actions.dismissFailure),
                )
                Spacer(Modifier.height(space.md))
            }
            StageButton(
                if (entry == Entry.Taking(EntryWay.QUICK_PLAY)) words.entering else words.quickPlay,
                onClick = tapped("home.quick_play", onClick = actions.quickPlay),
                modifier = Modifier.fillMaxWidth(),
                size = ButtonSize.HERO,
                enabled = !taking,
                footer = {
                    // Its place is held before the first read, so the button never changes as the counts come.
                    val said = counts?.let { words.presence.fill(it.online, it.searching) }
                    Box(
                        Modifier
                            .graphicsLayer { alpha = if (said == null) 0f else 1f }
                            .then(if (said == null) Modifier.clearAndSetSemantics { } else Modifier),
                    ) {
                        PresenceStrip(counts?.online ?: 0, counts?.searching ?: 0, said.orEmpty(), framed = false)
                    }
                },
            )
            Spacer(Modifier.height(space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
                StageButton(
                    words.createRoom,
                    onClick = tapped("home.create_room", onClick = actions.createRoom),
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.SECONDARY,
                    icon = KvizicIcons.Plus,
                    iconAbove = true,
                    enabled = !taking,
                )
                StageButton(
                    words.joinByCode,
                    onClick = tapped("home.join_by_code", onClick = actions.joinByCode),
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.SECONDARY,
                    icon = KvizicIcons.Keypad,
                    iconAbove = true,
                    enabled = !taking,
                )
            }
            Spacer(Modifier.height(space.md))
            StageButton(
                words.publicRooms,
                onClick = tapped("home.public_rooms", onClick = actions.publicRooms),
                modifier = Modifier.fillMaxWidth(),
                kind = ButtonKind.DARK,
                icon = KvizicIcons.Globe,
                enabled = !taking,
                trailing = counts?.let { { Chip(it.publicRooms.toString(), tone = ChipTone.ACCENT) } },
            )
            Spacer(Modifier.height(space.xs))
            StageButton(
                if (entry == Entry.Taking(EntryWay.SOLO)) words.entering else words.solo,
                onClick = tapped("home.solo", onClick = actions.solo),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                kind = ButtonKind.QUIET,
                size = ButtonSize.SMALL,
                icon = KvizicIcons.Person,
                enabled = !taking,
            )
            if (state.profile == null && state.failure != null) {
                KvizicText(
                    strings.failureText(state.failure.error, state.failure.retryAfter),
                    Modifier.fillMaxWidth(),
                    style = type.caption,
                    color = colors.loss,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A line to read and take down: why the last room ended, or why a seat could not be taken. */
@Composable
internal fun Notice(
    text: String,
    onDismiss: () -> Unit,
) {
    val space = KvizicTheme.space
    Panel(Modifier.fillMaxWidth(), padding = space.md) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.md)) {
            KvizicText(text, Modifier.weight(1f))
            StageButton(
                LocalStrings.current.game.ok,
                onClick = onDismiss,
                kind = ButtonKind.QUIET,
                size = ButtonSize.SMALL,
            )
        }
    }
}
