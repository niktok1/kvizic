package io.ntole.kvizic.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.lobby.LobbyRules
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.LobbyVisibility
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.domain.topic.TopicGroup
import io.ntole.kvizic.design.component.CardStackArt
import io.ntole.kvizic.design.component.ClockArt
import io.ntole.kvizic.design.component.GaugeArt
import io.ntole.kvizic.design.component.PenaltyArt
import io.ntole.kvizic.design.component.RoundTableArt
import io.ntole.kvizic.design.component.SettingBubble
import io.ntole.kvizic.design.component.SettingTile
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.StepKey
import io.ntole.kvizic.design.component.TextInput
import io.ntole.kvizic.design.component.TileSwitch
import io.ntole.kvizic.design.component.TopicsButton
import io.ntole.kvizic.design.component.VisibilityArt
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.navigation.BackTopBar
import io.ntole.kvizic.navigation.SystemBack

/**
 * A room's settings, to make a room with or for its host to change: the topics, Све being none picked, on a fan of
 * cards over six tiles, each a setting's name, its picture and its control at its foot. How many questions (a stack
 * of cards), how long each (the question's clock), how hard (a gauge) and how many seats, at the least [minPlayers]
 * (a table), each step by step on a key split in two; who may find the room and whether a wrong answer costs points
 * on switches. A tap on a tile's name or picture says what it is in a bubble over it. The room's name is optional:
 * while it has none, its field shows [hostName], the name the room goes by. [settings] is what is picked, which
 * [onChange] changes; [onDone] makes the room or
 * saves the change, its button [doneLabel].
 *
 * The topics open the [TopicPicker] in the settings' place, searched and grouped by [groups]; back closes it.
 *
 * The screen draws its own top bar, [title] and the way back, [onBack], so the picker's bar stands in its
 * place rather than under it; [notice] stands under the bar, over the settings. With no [onBack], no bar.
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
    title: String? = null,
    onBack: (() -> Unit)? = null,
    notice: @Composable () -> Unit = {},
    hostName: String? = null,
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
    var explained by rememberSaveable { mutableStateOf<Int?>(null) }
    val tiles =
        settingTiles(settings, minPlayers) { changed ->
            explained = null
            onChange(changed)
        }
    val pool = if (settings.topics.isEmpty()) topics else topics.filter { it.id in settings.topics }
    PageUnderBar(title = title, onBack = onBack, above = notice) {
        Column(
            modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            TopicsButton(
                label = words.topics,
                names = topicsSummary(settings.topics, topics, language, words.allTopics),
                detail =
                    pool.takeIf { it.isNotEmpty() }?.let {
                        words.topicCount.of(it.size, language) + " · " +
                            words.questionCount.of(it.sumOf { topic -> topic.questionCount }, language)
                    },
                onClick = tapped("settings.topics") { picking = true },
            )
            tiles.chunked(TILES_IN_A_ROW).forEachIndexed { row, three ->
                Box(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        three.forEachIndexed { column, tile ->
                            val at = row * TILES_IN_A_ROW + column
                            SettingTile(
                                name = tile.name,
                                said = tile.said,
                                onExplain =
                                    tapped("settings.explain", mapOf("setting" to tile.id)) {
                                        explained = if (explained == at) null else at
                                    },
                                control = tile.control,
                                modifier = Modifier.weight(1f),
                                explained = explained == at,
                                art = { tile.art() },
                            )
                        }
                    }
                    explained?.takeIf { it / TILES_IN_A_ROW == row }?.let { at ->
                        SettingBubble(
                            title = tiles[at].name,
                            text = tiles[at].said,
                            index = at % TILES_IN_A_ROW,
                            count = TILES_IN_A_ROW,
                            gap = space.sm,
                            onClose = tapped("settings.explain", mapOf("setting" to tiles[at].id)) { explained = null },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            TextInput(
                value = settings.name.orEmpty(),
                onValueChange = {
                    onChange(
                        settings.copy(
                            name = it.take(LobbyRules.MAX_ROOM_NAME_LENGTH).takeIf { name -> name.isNotEmpty() },
                        ),
                    )
                },
                label = words.roomName,
                placeholder = hostName?.let { shown(it) } ?: words.roomNameHint,
                supporting =
                    LocalStrings.current.roomSettings.unnamed.takeIf {
                        hostName != null &&
                            settings.name == null
                    },
            )
        }
        Spacer(Modifier.height(space.md))
        StageButton(
            doneLabel,
            onClick = tapped("settings.done", onClick = onDone),
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
        )
    }
}

private class Tile(
    val id: String,
    val name: String,
    val said: String,
    val art: @Composable () -> Unit,
    val control: @Composable () -> Unit,
)

@Composable
private fun settingTiles(
    settings: LobbySettings,
    minPlayers: Int,
    change: (LobbySettings) -> Unit,
): List<Tile> {
    val strings = LocalStrings.current
    val words = strings.game
    val said = strings.roomSettings
    val language = LocalLanguage.current
    val public = settings.visibility == LobbyVisibility.PUBLIC
    val level = settings.difficulty
    val levelSaid =
        when (level) {
            LobbyDifficulty.VERY_EASY -> said.veryEasySaid
            LobbyDifficulty.EASY -> said.easySaid
            LobbyDifficulty.MEDIUM -> said.mediumSaid
            LobbyDifficulty.HARD -> said.hardSaid
        }
    val seats = (minPlayers.coerceAtLeast(LobbyRules.MIN_PLAYERS)..LobbyRules.MAX_PLAYERS).toList()
    val next = if (public) LobbyVisibility.PRIVATE else LobbyVisibility.PUBLIC
    val flipVisibility =
        tapped("settings.visibility", mapOf("visibility" to next.name.lowercase())) {
            change(settings.copy(visibility = next))
        }
    val flipPenalty =
        tapped("settings.penalty") { change(settings.copy(wrongAnswerPenalty = !settings.wrongAnswerPenalty)) }
    return listOf(
        Tile(
            "questions",
            words.questions,
            said.questionsSaid.fill(words.questionCount.of(settings.questionCount, language)),
            art = {
                CardStackArt(
                    settings.questionCount,
                    LobbyRules.QUESTION_COUNTS.first(),
                    LobbyRules.QUESTION_COUNTS.last(),
                )
            },
            control = {
                Steps(
                    LobbyRules.QUESTION_COUNTS,
                    settings.questionCount,
                    words.questions,
                    "settings.questions",
                    "count",
                ) {
                    change(settings.copy(questionCount = it))
                }
            },
        ),
        Tile(
            "time",
            words.time,
            said.timeSaid.fill(words.seconds.fill(settings.secondsPerQuestion)),
            art = { ClockArt(settings.secondsPerQuestion) },
            control = {
                Steps(LobbyRules.ANSWER_SECONDS, settings.secondsPerQuestion, words.time, "settings.time", "seconds") {
                    change(settings.copy(secondsPerQuestion = it))
                }
            },
        ),
        Tile(
            "difficulty",
            words.difficulty,
            words.levelName(level) + ": " + levelSaid,
            art = { GaugeArt(level.ordinal, LobbyDifficulty.entries.size) },
            control = {
                Steps(
                    LobbyDifficulty.entries,
                    level,
                    words.difficulty,
                    "settings.difficulty",
                    "difficulty",
                    { it.name.lowercase() },
                ) {
                    change(settings.copy(difficulty = it))
                }
            },
        ),
        Tile(
            "players",
            words.players,
            said.playersSaid.fill(settings.maxPlayers),
            art = { RoundTableArt(settings.maxPlayers, LobbyRules.MAX_PLAYERS) },
            control = {
                Steps(seats, settings.maxPlayers, words.players, "settings.players", "players") {
                    change(settings.copy(maxPlayers = it))
                }
            },
        ),
        Tile(
            "visibility",
            said.entry,
            if (public) said.publicSaid else said.privateSaid,
            art = { VisibilityArt(public) },
            control = {
                TileSwitch(
                    public,
                    { flipVisibility() },
                    words.publicShort,
                    offIcon = KvizicIcons.Lock,
                    onIcon = KvizicIcons.Globe,
                )
            },
        ),
        Tile(
            "penalty",
            words.penaltyOn,
            if (settings.wrongAnswerPenalty) said.penaltyOnSaid else said.penaltyOffSaid,
            art = { PenaltyArt(settings.wrongAnswerPenalty) },
            control = { TileSwitch(settings.wrongAnswerPenalty, { flipPenalty() }, words.wrongAnswerPenalty) },
        ),
    )
}

@Composable
private fun <T> Steps(
    options: List<T>,
    current: T,
    name: String,
    element: String,
    property: String,
    shown: (T) -> Any = { it as Any },
    onPick: (T) -> Unit,
) {
    val said = LocalStrings.current.roomSettings
    val at = options.indexOf(current)
    val less = options.getOrNull(at - 1)
    val more = options.getOrNull(at + 1)
    StepKey(
        onLess = less?.let { tapped(element, mapOf(property to shown(it))) { onPick(it) } },
        onMore = more?.let { tapped(element, mapOf(property to shown(it))) { onPick(it) } },
        lessLabel = "$name, ${said.less}",
        moreLabel = "$name, ${said.more}",
    )
}

private const val TILES_IN_A_ROW = 3

/**
 * A room's [Page] under a screen's top bar, [title] and the way back, [onBack], the bar where every screen's
 * stands; [above] between the two. With no [onBack], the page alone.
 */
@Composable
internal fun PageUnderBar(
    title: String?,
    onBack: (() -> Unit)?,
    above: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (onBack != null) BackTopBar(onBack = onBack, title = title)
        above()
        Box(Modifier.weight(1f)) { Page(content) }
    }
}
