package io.ntole.kvizic.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.lobby.LobbyRules
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.LobbyVisibility
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.domain.topic.TopicGroup
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.KvizicIcon
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.Toggle
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.navigation.SystemBack

/**
 * A room's settings, to make a room with or for its host to change: how many questions and how long each,
 * which topics, Све being none picked, how hard, how many seats, at the least [minPlayers] (the members a
 * room already has), who may find it, and whether a wrong answer costs points. [settings] is what is picked,
 * which [onChange] changes; [onDone] makes the room or saves the change, its button [doneLabel], with [note]
 * over it: that a change made during a game is the next game's.
 *
 * The topics are one line, what is picked in a few words, which opens the [TopicPicker] in the settings'
 * place, searched and grouped by [groups], for the hundreds of topics to come; back closes it.
 */
@Composable
fun SettingsScreen(
    settings: LobbySettings,
    topics: List<Topic>,
    onChange: (LobbySettings) -> Unit,
    doneLabel: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    minPlayers: Int = LobbyRules.MIN_PLAYERS,
    enabled: Boolean = true,
    groups: List<TopicGroup> = emptyList(),
    note: String? = null,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    SystemBack(enabled = picking) { picking = false }
    if (picking) {
        TopicPicker(
            picked = settings.topics,
            topics = topics,
            groups = groups,
            questionCount = settings.questionCount,
            onChange = { onChange(settings.copy(topics = it)) },
            onDone = { picking = false },
        )
        return
    }
    val words = LocalStrings.current.game
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val heading: @Composable (String) -> Unit = { text ->
        KvizicText(text, style = type.label, color = colors.onPageMuted)
        Spacer(Modifier.height(space.xs))
    }
    Page {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(space.md),
        ) {
            Column {
                heading(words.questions)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                    LobbyRules.QUESTION_COUNTS.forEach { count ->
                        Chip(
                            count.toString(),
                            selected = settings.questionCount == count,
                            onClick =
                                tapped(
                                    "settings.questions",
                                    mapOf("count" to count),
                                ) { onChange(settings.copy(questionCount = count)) },
                        )
                    }
                }
            }
            Column {
                heading(words.time)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                    LobbyRules.ANSWER_SECONDS.forEach { seconds ->
                        Chip(
                            words.seconds.fill(seconds),
                            selected = settings.secondsPerQuestion == seconds,
                            onClick =
                                tapped("settings.time", mapOf("seconds" to seconds)) {
                                    onChange(settings.copy(secondsPerQuestion = seconds))
                                },
                        )
                    }
                }
            }
            Column {
                heading(words.topics)
                StageButton(
                    topicsSummary(settings.topics, topics, language, words.allTopics),
                    onClick = tapped("settings.topics") { picking = true },
                    modifier = Modifier.fillMaxWidth(),
                    kind = ButtonKind.DARK,
                    size = ButtonSize.SMALL,
                    trailing = { KvizicIcon(KvizicIcons.ChevronRight, contentDescription = null) },
                )
            }
            Column {
                heading(words.difficulty)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                    LobbyDifficulty.entries.forEach { difficulty ->
                        Chip(
                            words.levelName(difficulty),
                            selected = settings.difficulty == difficulty,
                            onClick =
                                tapped("settings.difficulty", mapOf("difficulty" to difficulty.name.lowercase())) {
                                    onChange(settings.copy(difficulty = difficulty))
                                },
                        )
                    }
                }
            }
            Column {
                heading(words.players)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                    verticalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    (minPlayers.coerceAtLeast(LobbyRules.MIN_PLAYERS)..LobbyRules.MAX_PLAYERS).forEach { players ->
                        Chip(
                            players.toString(),
                            selected = settings.maxPlayers == players,
                            onClick =
                                tapped(
                                    "settings.players",
                                    mapOf("players" to players),
                                ) { onChange(settings.copy(maxPlayers = players)) },
                        )
                    }
                }
            }
            Column {
                heading(words.whoCanJoin)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
                    val ways = listOf(LobbyVisibility.PRIVATE to words.codeOnly, LobbyVisibility.PUBLIC to words.anyone)
                    ways.forEach { (visibility, label) ->
                        Chip(
                            label,
                            selected = settings.visibility == visibility,
                            onClick =
                                tapped("settings.visibility", mapOf("visibility" to visibility.name.lowercase())) {
                                    onChange(settings.copy(visibility = visibility))
                                },
                        )
                    }
                }
            }
            val penalty =
                tapped(
                    "settings.penalty",
                ) { onChange(settings.copy(wrongAnswerPenalty = !settings.wrongAnswerPenalty)) }
            Toggle(
                checked = settings.wrongAnswerPenalty,
                onCheckedChange = { penalty() },
                label = words.wrongAnswerPenalty,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(space.md))
        if (note != null) {
            KvizicText(
                note,
                Modifier.fillMaxWidth(),
                style = type.caption,
                color = colors.onPageMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(space.sm))
        }
        StageButton(
            doneLabel,
            onClick = tapped("settings.done", onClick = onDone),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }
}
