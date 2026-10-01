package io.ntole.kvizic.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.design.component.AnswerGrid
import io.ntole.kvizic.design.component.AnswerTileState
import io.ntole.kvizic.design.component.Avatar
import io.ntole.kvizic.design.component.AvatarChip
import io.ntole.kvizic.design.component.AvatarSize
import io.ntole.kvizic.design.component.AvatarStack
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.CodeDisplay
import io.ntole.kvizic.design.component.FlapSize
import io.ntole.kvizic.design.component.FlipNumber
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.component.PanelKind
import io.ntole.kvizic.design.component.Podium
import io.ntole.kvizic.design.component.PodiumPlace
import io.ntole.kvizic.design.component.QuestionText
import io.ntole.kvizic.design.component.QuestionTimer
import io.ntole.kvizic.design.component.ReactionBurst
import io.ntole.kvizic.design.component.ScoreRow
import io.ntole.kvizic.design.component.Scoreboard
import io.ntole.kvizic.design.component.SeatGrid
import io.ntole.kvizic.design.component.SeatOccupant
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.StageIconButton
import io.ntole.kvizic.design.component.TimerPhase
import io.ntole.kvizic.design.component.TimerSize
import io.ntole.kvizic.design.component.WaitingFor
import io.ntole.kvizic.design.component.Wordmark
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme

/*
 * The screens of the game as the owner approves them, built from the design system's components and
 * tokens alone, as a real screen will be: every size a token, every word Serbian Cyrillic, nothing
 * drawn here that a component does not draw.
 */

/** A member of the room in the mockups. */
internal data class Member(
    val name: String,
    val avatar: String,
    val seat: Int,
    val host: Boolean = false,
) {
    val chip: AvatarChip get() = AvatarChip(avatar, seat)
}

internal val Nina = Member("Нина", "fox", seat = 0, host = true)
internal val Sova = Member("Мудра Сова", "owl", seat = 1)
internal val Marko = Member("Марко", "hedgehog", seat = 2)
internal val Bojan = Member("Бојан", "bear", seat = 3)
internal val Roda = Member("Тиха Рода", "stork", seat = 4)
internal val Room = listOf(Nina, Sova, Marko, Bojan, Roda)

/** A full room of eight, for an easy question most of it answers alike. */
internal val FullRoom =
    Room +
        listOf(
            Member("Лана", "owl", seat = 5),
            Member("Стефан", "bear", seat = 6),
            Member("Ива", "fox", seat = 7),
        )

internal const val QUESTION = "Која река протиче кроз Нови Сад?"
internal val RIVERS = listOf("Дунав", "Сава", "Тиса", "Морава")

/** A long question and long answers, as the rules allow them: set smaller, never cut. */
internal const val LONG_QUESTION =
    "Која река, дуга више од 2.800 километара, протиче кроз четири престонице: Беч, Братиславу, Будимпешту и Београд?"
internal val LONG_ANSWERS =
    listOf(
        "Дунав, друга по дужини река у Европи",
        "Рајна, која извире у Алпима и улива се у море код Ротердама",
        "Дњепар, који извире у Русији и тече кроз Кијев до Црног мора",
        "Волга, најдужа река у Европи, улива се у Каспијско језеро",
    )

// 1. Home: the quick game first, the rest under it, solo last and small.
@Composable
internal fun HomeMock() {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Screen {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.md)) {
            Avatar("fox", seat = 0, size = AvatarSize.SM, contentDescription = "Нина")
            Column(Modifier.weight(1f)) {
                KvizicText("Нина", style = type.name)
                KvizicText("12 игара · 3 победе", style = type.caption, color = colors.onPageMuted)
            }
            StageIconButton(KvizicIcons.Sliders, contentDescription = "Подешавања", onClick = {}, small = true)
        }
        Spacer(Modifier.height(space.xl))
        Wordmark("Квизић", Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        StageButton("Брза игра", onClick = {}, modifier = Modifier.fillMaxWidth(), size = ButtonSize.HERO)
        Spacer(Modifier.height(space.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(space.sm).drawBehind { drawCircle(colors.gain) })
            Spacer(Modifier.width(space.sm))
            KvizicText("128 на мрежи · 7 тражи игру", style = type.caption, color = colors.onPageMuted)
        }
        Spacer(Modifier.height(space.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
            StageButton(
                "Направи собу",
                onClick = {},
                modifier = Modifier.weight(1f),
                kind = ButtonKind.SECONDARY,
                icon = KvizicIcons.Plus,
                iconAbove = true,
            )
            StageButton(
                "Уђи кодом",
                onClick = {},
                modifier = Modifier.weight(1f),
                kind = ButtonKind.SECONDARY,
                icon = KvizicIcons.Keypad,
                iconAbove = true,
            )
        }
        Spacer(Modifier.height(space.md))
        StageButton(
            "Јавне собе",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            kind = ButtonKind.DARK,
            icon = KvizicIcons.Globe,
            trailing = { Chip("12", tone = ChipTone.ACCENT) },
        )
        Spacer(Modifier.height(space.xs))
        StageButton(
            "Соло",
            onClick = {},
            modifier = Modifier.align(Alignment.CenterHorizontally),
            kind = ButtonKind.QUIET,
            size = ButtonSize.SMALL,
            icon = KvizicIcons.Person,
        )
    }
}

// 2. The lobby, waiting: the code on flaps, the seats, the settings, reactions, and the host's start.
@Composable
internal fun LobbyMock(burst: Any? = "boban") {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Screen {
        TopBar(title = "Приватна соба") {
            StageIconButton(KvizicIcons.Share, contentDescription = "Подели собу", onClick = {}, small = true)
        }
        Spacer(Modifier.height(space.md))
        KvizicText(
            "Код собе",
            modifier = Modifier.fillMaxWidth(),
            style = type.label,
            color = colors.onPageMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(space.sm))
        CodeDisplay("482915", Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(space.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KvizicText("Играчи", style = type.label, color = colors.onPageMuted)
            Spacer(Modifier.width(space.sm))
            KvizicText("5 / 8", style = type.label, color = colors.onPageAccent)
        }
        Spacer(Modifier.height(space.sm))
        Box {
            Seats(Room + List(3) { null })
            // Бојан's flame, bursting up out of the top of his seat.
            ReactionBurst(
                KvizicIcons.Flame,
                burstKey = burst,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = space.sm, y = -space.xl),
            )
        }
        Spacer(Modifier.height(space.sm))
        KvizicText(
            "Чекамо још играча · позови друштво кодом",
            modifier = Modifier.fillMaxWidth(),
            style = type.caption,
            color = colors.onPageMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
            Chip("10 питања")
            Chip("15 с", icon = KvizicIcons.Clock)
            Chip("Све теме")
            Chip("Минус", icon = KvizicIcons.Check, tone = ChipTone.ACCENT)
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            val names = listOf("Браво", "Аплауз", "Ватра", "Вау", "Смех", "Упс")
            names.zip(KvizicIcons.REACTIONS).forEach { (name, icon) ->
                StageIconButton(icon, contentDescription = name, onClick = {}, small = true)
            }
        }
        Spacer(Modifier.height(space.md))
        StageButton(
            "Почни игру",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            supportingText = "5 играча · 10 питања",
        )
    }
}

/** A room's eight seats, the design system's, the host's titled. */
@Composable
private fun Seats(members: List<Member?>) {
    SeatGrid(
        members.map { member ->
            member?.let {
                SeatOccupant(
                    it.name,
                    it.avatar,
                    it.seat,
                    badge =
                        "водитељ".takeIf { _ ->
                            it.host
                        },
                    host = it.host,
                )
            }
        },
        emptyDescription = "слободно",
    )
}

// 3. The question read alone: no answers yet, the lights coming up round the clock.
@Composable
internal fun ReadingMock(question: String = QUESTION) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Screen {
        RoundBar(round = 3, topic = "Географија", score = 1240)
        Spacer(Modifier.height(space.xl))
        QuestionTimer(
            TimerPhase.Reading(totalMillis = 15_000, readMillis = 2_400, readLeftMillis = 850),
            Modifier.align(Alignment.CenterHorizontally),
            contentDescription = "15 секунди за одговор",
        )
        Spacer(Modifier.height(space.xl))
        Panel(Modifier.fillMaxWidth(), kind = PanelKind.SCREEN, padding = space.xl) {
            QuestionText(question, Modifier.fillMaxWidth(), reading = true)
        }
        Spacer(Modifier.height(space.xl))
        // Where the answers will stand, so nothing moves when they come.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.tile.gap)) {
            repeat(2) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(space.tile.gap)) {
                    repeat(2) { Panel(Modifier.weight(1f).fillMaxSize(), kind = PanelKind.EMPTY) {} }
                }
            }
        }
        Spacer(Modifier.height(space.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KvizicText("Одговори стижу…", style = type.label, color = colors.onPageMuted)
        }
    }
}

// 4. Answering: four buzzers under the question, the clock running, who has answered.
@Composable
internal fun AnsweringMock(
    question: String = QUESTION,
    options: List<String> = RIVERS,
) {
    QuestionScreen(
        options = options,
        timer = TimerPhase.Running(totalMillis = 15_000, leftMillis = 11_300),
        states = options.map { AnswerTileState.IDLE },
        answered = listOf(Sova, Marko, Bojan),
        question = question,
    )
}

// 5. Locked in: the player's buzzer down and lit, the others dark, and everyone's picks on them.
@Composable
internal fun LockedInMock(
    question: String = QUESTION,
    options: List<String> = RIVERS,
    picks: Map<Int, List<Member>> = mapOf(0 to listOf(Nina, Marko), 1 to listOf(Sova), 2 to listOf(Bojan)),
) {
    QuestionScreen(
        question = question,
        options = options,
        timer = TimerPhase.Running(totalMillis = 15_000, leftMillis = 7_300),
        states =
            listOf(
                AnswerTileState.LOCKED_IN,
                AnswerTileState.DIMMED,
                AnswerTileState.DIMMED,
                AnswerTileState.DIMMED,
            ),
        answered = listOf(Nina, Sova, Marko, Bojan),
        picks = picks,
    )
}

// 6. The reveal: the right buzzer lit, who got it first, and the points won and lost.
@Composable
internal fun RevealMock(
    question: String = QUESTION,
    options: List<String> = RIVERS,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Screen {
        RoundBar(round = 3, topic = "Географија", score = 1328)
        Spacer(Modifier.height(space.md))
        Panel(Modifier.fillMaxWidth(), kind = PanelKind.SCREEN, padding = space.lg) {
            QuestionText(question, Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(space.md))
        AnswerGrid(
            options = options,
            modifier = Modifier.weight(1f),
            states =
                listOf(
                    AnswerTileState.CORRECT,
                    AnswerTileState.DIMMED,
                    AnswerTileState.DIMMED,
                    AnswerTileState.DIMMED,
                ),
            pickers = { i ->
                when (i) {
                    0 -> OrderedPickers(listOf(Nina, Marko))
                    1 -> AvatarStack(listOf(Sova.chip))
                    2 -> AvatarStack(listOf(Bojan.chip))
                    else -> Unit
                }
            },
            crowd = 4,
        )
        Spacer(Modifier.height(space.md))
        Scoreboard(
            listOf(
                Standing(Nina, 1328, 88),
                Standing(Marko, 1210, 76),
                Standing(Bojan, 1045, 0),
                Standing(Sova, 980, -25),
            ),
        )
        Spacer(Modifier.height(space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KvizicText("Следеће питање за 4 с", style = type.caption, color = colors.onPageMuted)
            Spacer(Modifier.weight(1f))
            StageButton(
                "Пријави питање",
                onClick = {},
                kind = ButtonKind.QUIET,
                size = ButtonSize.SMALL,
                icon = KvizicIcons.Flag,
            )
        }
    }
}

// 7. A true or false question: two buzzers, stacked tall.
@Composable
internal fun TrueFalseMock() {
    QuestionScreen(
        question = "Сава се улива у Дунав у Београду.",
        options = listOf("Тачно", "Нетачно"),
        timer = TimerPhase.Running(totalMillis = 10_000, leftMillis = 6_200),
        states = listOf(AnswerTileState.IDLE, AnswerTileState.IDLE),
        answered = listOf(Sova, Bojan),
        round = 4,
        topic = "Географија",
        score = 1328,
    )
}

// 8. A question of three answers, revealed: the player's buzzer wrong, the right one lit.
@Composable
internal fun ThreeAnswersMock() {
    QuestionScreen(
        question = "Која планета је најближа Сунцу?",
        options = listOf("Меркур", "Венера", "Марс"),
        timer = TimerPhase.Stopped(totalMillis = 15_000, leftMillis = 0),
        states = listOf(AnswerTileState.CORRECT, AnswerTileState.WRONG, AnswerTileState.DIMMED),
        answered = Room,
        picks = mapOf(0 to listOf(Marko, Sova, Roda), 1 to listOf(Nina, Bojan)),
        round = 5,
        topic = "Наука и технологија",
        score = 1303,
        ordered = true,
        verdict = "Нетачно · −25",
    )
}

// 12 to 14. A full room on an easy question: seven of eight locked in on one answer, in a grid of short
// answers and a column of long ones, and the right answer revealed with who got it first.
@Composable
internal fun CrowdMock(
    question: String = QUESTION,
    options: List<String> = RIVERS,
    revealed: Boolean = false,
) {
    val onFirst = FullRoom.filter { it != Roda }
    QuestionScreen(
        question = question,
        options = options,
        timer =
            if (revealed) {
                TimerPhase.Stopped(totalMillis = 15_000, leftMillis = 9_100)
            } else {
                TimerPhase.Running(totalMillis = 15_000, leftMillis = 9_800)
            },
        states =
            if (revealed) {
                listOf(AnswerTileState.CORRECT, AnswerTileState.DIMMED, AnswerTileState.DIMMED, AnswerTileState.DIMMED)
            } else {
                listOf(
                    AnswerTileState.LOCKED_IN,
                    AnswerTileState.DIMMED,
                    AnswerTileState.DIMMED,
                    AnswerTileState.DIMMED,
                )
            },
        answered = onFirst + if (revealed) listOf(Roda) else emptyList(),
        room = FullRoom,
        picks = if (revealed) mapOf(0 to onFirst, 3 to listOf(Roda)) else mapOf(0 to onFirst),
        ordered = revealed,
        verdict = if (revealed) "Тачно · +97" else null,
    )
}

// 9. The results: the podium, the rest, and each player's own way back.
@Composable
internal fun ResultsMock() {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Screen {
        Spacer(Modifier.height(space.md))
        KvizicText(
            "Крај игре · 10 питања",
            modifier = Modifier.fillMaxWidth(),
            style = type.label,
            color = colors.onPageMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(space.xs))
        KvizicText("Победила је Нина", Modifier.fillMaxWidth(), style = type.headline, textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Podium(
            listOf(
                PodiumPlace(Nina.name, Nina.avatar, Nina.seat, 1612),
                PodiumPlace(Marko.name, Marko.avatar, Marko.seat, 1488),
                PodiumPlace(Bojan.name, Bojan.avatar, Bojan.seat, 1210),
            ),
            Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(space.lg))
        Scoreboard(listOf(Standing(Sova, 980, null, place = 4), Standing(Roda, 655, null, place = 5)))
        Spacer(Modifier.height(space.md))
        Row(
            horizontalArrangement = Arrangement.spacedBy(space.xs),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Chip("Тачно 8 / 10", tone = ChipTone.GAIN)
            Chip("Прва 4 пута", icon = KvizicIcons.Bolt)
            Chip("Географија", icon = KvizicIcons.Crown)
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
            StageButton("Назад у собу", onClick = {}, modifier = Modifier.weight(1f))
            StageButton("Изађи", onClick = {}, kind = ButtonKind.DARK, icon = KvizicIcons.Leave)
        }
    }
}

/** A question on its screen, its answers, the clock and who has answered: the screen of play. */
@Composable
private fun QuestionScreen(
    options: List<String>,
    timer: TimerPhase,
    states: List<AnswerTileState>,
    answered: List<Member>,
    question: String = QUESTION,
    room: List<Member> = Room,
    picks: Map<Int, List<Member>> = emptyMap(),
    round: Int = 3,
    topic: String = "Географија",
    score: Int = 1240,
    ordered: Boolean = false,
    verdict: String? = null,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    Screen {
        RoundBar(round = round, topic = topic, score = score, timer = timer)
        Spacer(Modifier.height(space.md))
        Panel(Modifier.fillMaxWidth(), kind = PanelKind.SCREEN, padding = space.lg) {
            QuestionText(question, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(space.lg))
        AnswerGrid(
            options = options,
            modifier = Modifier.weight(1f),
            states = states,
            onPick = if (states.all { it == AnswerTileState.IDLE }) ({}) else null,
            pickers =
                if (picks.isEmpty()) {
                    null
                } else {
                    { i ->
                        val who = picks[i].orEmpty()
                        val first = ordered && states[i] == AnswerTileState.CORRECT
                        when {
                            who.isEmpty() -> Unit
                            first -> OrderedPickers(who)
                            else -> AvatarStack(who.map { it.chip })
                        }
                    }
                },
            crowd = room.size,
        )
        Spacer(Modifier.height(space.md))
        BottomStrip(room, answered, verdict)
    }
}

/** The round, its topic, and the player's score on flaps. */
@Composable
private fun RoundBar(
    round: Int,
    topic: String,
    score: Int,
    timer: TimerPhase? = null,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KvizicText("Питање", style = type.label, color = colors.onPageMuted)
                Spacer(Modifier.width(space.xs))
                KvizicText("$round / 10", style = type.label, color = colors.onPageAccent)
            }
            Spacer(Modifier.height(space.xs))
            Chip(topic)
        }
        // While the answers are up, the clock stands in the middle of the bar, where it leaves the
        // question the whole width under it.
        if (timer != null) {
            QuestionTimer(
                timer,
                size = TimerSize.SMALL,
                contentDescription = "${timer.totalMillis / 1_000} секунди за одговор",
            )
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            KvizicText("Поени", style = type.label, color = colors.onPageMuted)
            Spacer(Modifier.height(space.xs))
            FlipNumber(score, size = FlapSize.MEDIUM)
        }
    }
}

/**
 * Under the answers: who the question still waits for, the player among them until they answer, and once
 * it is revealed the player's verdict, in a strip as tall either way.
 */
@Composable
private fun BottomStrip(
    room: List<Member>,
    answered: List<Member>,
    verdict: String?,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    Panel(Modifier.fillMaxWidth(), padding = space.md) {
        if (verdict != null) {
            Box(Modifier.fillMaxWidth().heightIn(min = space.avatar.xs), contentAlignment = Alignment.Center) {
                KvizicText(verdict, style = type.bodyStrong)
            }
        } else {
            val waiting = room.filter { it !in answered }
            WaitingFor(
                waiting.map { it.chip },
                Modifier.fillMaxWidth(),
                contentDescription = "Чека се: " + waiting.joinToString { it.name },
            )
        }
    }
}

/** Those who picked the right answer, in the order they got it, the first three with their place. */
@Composable
private fun OrderedPickers(members: List<Member>) {
    AvatarStack(
        members.mapIndexed { i, member -> AvatarChip(member.avatar, member.seat, order = (i + 1).takeIf { it <= 3 }) },
        contentDescription = members.joinToString { it.name },
    )
}

/** A player's place in the standings, and what the last question won or lost them. */
internal data class Standing(
    val member: Member,
    val total: Int,
    val delta: Int?,
    val place: Int? = null,
)

/** The standings, on the design system's board. */
@Composable
private fun Scoreboard(rows: List<Standing>) {
    Scoreboard(
        rows.mapIndexed { i, row ->
            ScoreRow(
                place = row.place ?: (i + 1),
                name = row.member.name,
                avatarId = row.member.avatar,
                seat = row.member.seat,
                total = row.total,
                delta = row.delta,
                host = row.member.host,
            )
        },
        Modifier.fillMaxWidth(),
    )
}

/** A screen's top: back, its title, and what else it offers. */
@Composable
private fun TopBar(
    title: String,
    actions: @Composable () -> Unit,
) {
    val type = KvizicTheme.type
    Row(verticalAlignment = Alignment.CenterVertically) {
        StageIconButton(KvizicIcons.Back, contentDescription = "Назад", onClick = {}, small = true)
        KvizicText(title, Modifier.weight(1f), style = type.label, textAlign = TextAlign.Center)
        actions()
    }
}

/** A screen's page: its padding from the edges, top to bottom. */
@Composable
private fun Screen(content: @Composable ColumnScope.() -> Unit) {
    val space = KvizicTheme.space
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = space.screen, vertical = space.md),
        content = content,
    )
}
