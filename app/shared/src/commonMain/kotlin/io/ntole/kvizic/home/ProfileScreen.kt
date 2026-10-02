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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.analytics.tappedAt
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.design.avatar.AvatarArt
import io.ntole.kvizic.design.component.Avatar
import io.ntole.kvizic.design.component.AvatarSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.component.PanelKind
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.cued
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.failureText
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.loading.LoadingSpinner
import io.ntole.kvizic.room.levelName
import io.ntole.kvizic.room.shown
import io.ntole.kvizic.room.topicNameOf

/**
 * The player as the server knows them ([state]): their avatar and name, their games, wins, share of answers
 * right, best solo score and best topic, and every avatar to pick from, which [onPick] asks the server for.
 * [topics] name the best topic.
 */
@Composable
fun ProfileScreen(
    state: HomeState,
    topics: List<Topic>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val words = strings.game
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val profile = state.profile
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        if (profile == null) {
            LoadingSpinner(name = strings.loading, modifier = Modifier.padding(space.xl))
            return@Box
        }
        val pick = tappedAt("profile.avatar") { index -> onPick(AvatarArt.DRAWN[index]) }
        Column(
            Modifier
                .widthIn(max = space.contentWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.screen, vertical = space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space.md),
        ) {
            Avatar(state.changingAvatar ?: profile.avatarId, seat = 0, size = AvatarSize.XL)
            KvizicText(shown(profile.displayName), style = type.headline, textAlign = TextAlign.Center)
            val stats = profile.stats
            Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                Chip(words.games.of(stats.gamesPlayed, language))
                Chip(words.wins.of(stats.gamesWon, language), tone = ChipTone.ACCENT)
            }
            if (stats.answersGiven > 0) {
                KvizicText(
                    words.correctShare.fill(stats.answersCorrect * PERCENT / stats.answersGiven),
                    style = type.body,
                    color = colors.onPageMuted,
                )
            }
            if (stats.soloBests.isNotEmpty()) {
                val bests =
                    LobbyDifficulty.entries.mapNotNull { level ->
                        stats.soloBests[level]?.let { "${words.levelName(level)} $it" }
                    }
                KvizicText(
                    words.soloBest.fill(bests.joinToString(" · ")),
                    style = type.body,
                    color = colors.onPageMuted,
                )
            }
            stats.bestTopicId?.let { id ->
                KvizicText(
                    words.bestTopic.fill(topicNameOf(id, topics, language)),
                    style = type.body,
                    color = colors.onPageMuted,
                )
            }
            Spacer(Modifier.height(space.sm))
            KvizicText(words.pickAvatar, Modifier.fillMaxWidth(), style = type.label, color = colors.onPageMuted)
            state.avatarFailure?.let { failure ->
                KvizicText(
                    strings.failureText(failure.error, failure.retryAfter),
                    style = type.caption,
                    color = colors.loss,
                )
            }
            AvatarArt.DRAWN.withIndex().chunked(AVATARS_A_ROW).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { (index, id) ->
                        val chosen = id == (state.changingAvatar ?: profile.avatarId)
                        Panel(
                            Modifier
                                .semantics(mergeDescendants = true) {
                                    contentDescription = words.avatarNames.getOrElse(index) { id }
                                    selected = chosen
                                }.clickable(role = Role.Button, onClick = cued(Cue.TAP_SOFT) { pick(index) }),
                            kind = if (chosen) PanelKind.PLAIN else PanelKind.EMPTY,
                            padding = space.xs,
                        ) {
                            Avatar(id, seat = index, size = AvatarSize.LG)
                        }
                    }
                }
            }
        }
    }
}

private const val PERCENT = 100
private const val AVATARS_A_ROW = 4
