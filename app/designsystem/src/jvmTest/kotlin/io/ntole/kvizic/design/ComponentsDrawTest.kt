package io.ntole.kvizic.design

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import io.ntole.kvizic.design.avatar.AvatarArt
import io.ntole.kvizic.design.component.AnswerGrid
import io.ntole.kvizic.design.component.AnswerTile
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
import io.ntole.kvizic.design.component.KvizicIcon
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.component.PanelKind
import io.ntole.kvizic.design.component.Podium
import io.ntole.kvizic.design.component.PodiumPlace
import io.ntole.kvizic.design.component.QuestionTimer
import io.ntole.kvizic.design.component.ReactionBurst
import io.ntole.kvizic.design.component.Spinner
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.StageIconButton
import io.ntole.kvizic.design.component.TileArrangement
import io.ntole.kvizic.design.component.TimerPhase
import io.ntole.kvizic.design.component.TimerSize
import io.ntole.kvizic.design.component.Toggle
import io.ntole.kvizic.design.component.WaitingFor
import io.ntole.kvizic.design.component.Wordmark
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every component in every state, drawn in every skin: the design system's sheet. Each skin draws it
 * whole, every text and name of it reaching the semantics a screen reader reads; with `KVIZIC_DESIGN_DIR`
 * set, each sheet is also written there, for the owner to see a skin all at once.
 */
class ComponentsDrawTest {
    @Test
    fun `every component draws in every skin`() {
        Skins.ALL.forEach { skin ->
            val scene = stageScene(skin, WIDTH, HEIGHT, DENSITY) { Sheet() }
            try {
                val image = scene.renderUpTo(BURST_MID * MILLI)
                // Lower-cased, as a skin may set a word in capitals.
                val shown = scene.everyNode().flatMap { it.texts + it.descriptions }.map { it.lowercase() }
                EXPECTED.forEach { text ->
                    assertTrue(text.lowercase() in shown, "${skin.id}'s sheet shows no \"$text\"")
                }
                writeDesign("components-${skin.id}", image)
            } finally {
                scene.close()
            }
        }
    }

    @Composable
    private fun Sheet() {
        val space = KvizicTheme.space
        Column(
            modifier = Modifier.fillMaxSize().padding(space.xl),
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            Wordmark("Квизић", Modifier.width(space.xl * SIGN))
            Section("AnswerTile · ROW, and one held down by a finger")
            val held = remember { MutableInteractionSource() }
            LaunchedEffect(held) { held.emit(PressInteraction.Press(Offset.Zero)) }
            Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
                AnswerTile(
                    0,
                    PRESSED,
                    AnswerTileState.IDLE,
                    Modifier.weight(1f),
                    onClick = {},
                    interactionSource = held,
                )
                AnswerTileState.entries.forEachIndexed { i, state ->
                    AnswerTile(i, state.name.lowercase(), state, Modifier.weight(1f), onClick = {})
                }
            }
            Section("AnswerTile · STACK")
            val pickers = listOf(AvatarChip("owl", 1), AvatarChip("bear", 3, order = 1))
            val picked: @Composable () -> Unit = { AvatarStack(pickers) }
            Row(Modifier.height(space.tile.gridMinHeight), horizontalArrangement = Arrangement.spacedBy(space.md)) {
                AnswerTileState.entries.forEachIndexed { i, state ->
                    AnswerTile(
                        i + AnswerTileState.entries.size,
                        state.name.lowercase(),
                        state,
                        Modifier.weight(1f),
                        arrangement = TileArrangement.STACK,
                        pickers = picked.takeIf { i % 2 == 0 },
                    )
                }
            }
            Section("AnswerGrid · 2, 3, 4, 5")
            Row(Modifier.height(space.xl * GRID), horizontalArrangement = Arrangement.spacedBy(space.lg)) {
                listOf(
                    listOf("Тачно", "Нетачно"),
                    listOf("Меркур", "Венера", "Марс"),
                    listOf("Дунав", "Сава", "Тиса", "Морава"),
                    listOf("Један", "Два", "Три", "Четири", "Пет"),
                ).forEach { options -> AnswerGrid(options, Modifier.weight(1f).fillMaxSize(), onPick = {}) }
            }
            Section("Toggle · off, on, and off while disabled")
            Row(horizontalArrangement = Arrangement.spacedBy(space.lg)) {
                Toggle(checked = false, onCheckedChange = {}, label = "Минус", modifier = Modifier.weight(1f))
                Toggle(checked = true, onCheckedChange = {}, label = "Статистика", modifier = Modifier.weight(1f))
                Toggle(
                    checked = false,
                    onCheckedChange = {},
                    label = "Јавна",
                    modifier = Modifier.weight(1f),
                    enabled = false,
                )
            }
            Section("StageButton · StageIconButton")
            Row(
                horizontalArrangement = Arrangement.spacedBy(space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StageButton("Брза игра", onClick = {}, size = ButtonSize.HERO, supportingText = "за тренутак")
                ButtonKind.entries.forEach { kind -> StageButton(kind.name.lowercase(), onClick = {}, kind = kind) }
                StageButton("disabled", onClick = {}, enabled = false)
                StageButton(
                    "small",
                    onClick = {},
                    size = ButtonSize.SMALL,
                    kind = ButtonKind.SECONDARY,
                    icon = KvizicIcons.Plus,
                )
                StageIconButton(KvizicIcons.Share, "share", onClick = {})
                StageIconButton(KvizicIcons.Back, "back", onClick = {}, kind = ButtonKind.SECONDARY, small = true)
            }
            Section("Panel · Chip")
            Row(Modifier.height(space.xl * PANEL), horizontalArrangement = Arrangement.spacedBy(space.md)) {
                PanelKind.entries.forEach { kind ->
                    Panel(Modifier.weight(1f).fillMaxSize(), kind = kind) { KvizicText(kind.name.lowercase()) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                ChipTone.entries.forEach { tone -> Chip(tone.name.lowercase(), tone = tone) }
                Chip("selected", selected = true, onClick = {})
                Chip("icon", icon = KvizicIcons.Clock)
            }
            Section("FlipNumber · CodeDisplay")
            Row(
                horizontalArrangement = Arrangement.spacedBy(space.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FlipNumber(88, size = FlapSize.SMALL, signed = true)
                FlipNumber(-25, size = FlapSize.SMALL)
                FlipNumber(1_240)
                FlipNumber(7, size = FlapSize.LARGE, minDigits = 2)
                CodeDisplay("482915")
            }
            Section("QuestionTimer")
            Row(
                horizontalArrangement = Arrangement.spacedBy(space.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuestionTimer(TimerPhase.Waiting(15_000), contentDescription = "waiting")
                QuestionTimer(TimerPhase.Reading(15_000, 2_400, 1_200), contentDescription = "reading")
                QuestionTimer(TimerPhase.Running(15_000, 9_400), contentDescription = "running")
                QuestionTimer(TimerPhase.Running(15_000, 4_400), contentDescription = "warning")
                QuestionTimer(TimerPhase.Stopped(15_000, 0), size = TimerSize.SMALL, contentDescription = "stopped")
            }
            Section("Avatar · AvatarStack")
            Row(
                horizontalArrangement = Arrangement.spacedBy(space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (AvatarArt.DRAWN + "stork").forEachIndexed { seat, id ->
                    Avatar(id, seat, size = AvatarSize.XL, contentDescription = id)
                }
                AvatarSize.entries.forEachIndexed { seat, size -> Avatar("fox", seat, size = size) }
                Avatar("owl", 5, host = true)
                Avatar("bear", 6, dimmed = true)
                Avatar("hedgehog", 7, order = 2)
                AvatarStack(AvatarArt.DRAWN.mapIndexed { i, id -> AvatarChip(id, i) }, contentDescription = "stack")
                WaitingFor(listOf(AvatarChip("fox", 0), AvatarChip("stork", 4)), contentDescription = "waiting-for")
            }
            Section("Podium · ReactionBurst · Spinner · icons")
            Row(horizontalArrangement = Arrangement.spacedBy(space.xl), verticalAlignment = Alignment.Bottom) {
                Podium(
                    listOf(
                        PodiumPlace("Нина", "fox", 0, 1612),
                        PodiumPlace("Марко", "hedgehog", 2, 1488),
                        PodiumPlace("Бојан", "bear", 3, 1210),
                    ),
                    Modifier.width(space.xl * PODIUM),
                )
                Box(
                    Modifier.size(space.burst),
                    contentAlignment = Alignment.Center,
                ) { ReactionBurst(KvizicIcons.Heart, burstKey = 1) }
                Spinner(contentDescription = "spinner")
                Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                    ICONS.chunked(ICONS_A_ROW).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                            row.forEach { (name, icon) ->
                                KvizicIcon(icon, contentDescription = name, size = space.icon.large)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Section(title: String) {
        KvizicText(title, style = KvizicTheme.type.label, color = KvizicTheme.colors.onPageMuted)
    }

    private companion object {
        const val DENSITY = 2f
        const val WIDTH = 2_400
        const val HEIGHT = 4_200

        /** Mid-way through the sample burst, when the sheet is drawn. */
        const val BURST_MID = 300L

        // Sizes of the sheet's rows, in the skin's xl steps, so the sheet itself writes no dp.
        const val SIGN = 16
        const val GRID = 14
        const val PANEL = 4
        const val PODIUM = 14
        const val ICONS_A_ROW = 7
        const val PRESSED = "pressed"

        val ICONS =
            listOf(
                "Mic" to KvizicIcons.Mic,
                "Check" to KvizicIcons.Check,
                "Cross" to KvizicIcons.Cross,
                "Back" to KvizicIcons.Back,
                "Share" to KvizicIcons.Share,
                "Plus" to KvizicIcons.Plus,
                "Keypad" to KvizicIcons.Keypad,
                "Globe" to KvizicIcons.Globe,
                "Person" to KvizicIcons.Person,
                "Sliders" to KvizicIcons.Sliders,
                "ChevronRight" to KvizicIcons.ChevronRight,
                "Bolt" to KvizicIcons.Bolt,
                "Crown" to KvizicIcons.Crown,
                "Flag" to KvizicIcons.Flag,
                "Leave" to KvizicIcons.Leave,
                "Clock" to KvizicIcons.Clock,
                "Hourglass" to KvizicIcons.Hourglass,
                "Laugh" to KvizicIcons.Laugh,
                "Wow" to KvizicIcons.Wow,
                "Heart" to KvizicIcons.Heart,
                "Clap" to KvizicIcons.Clap,
                "Oops" to KvizicIcons.Oops,
                "Flame" to KvizicIcons.Flame,
                "ThumbUp" to KvizicIcons.ThumbUp,
            )

        val EXPECTED =
            AnswerTileState.entries.map { it.name.lowercase() } + PRESSED +
                listOf("Тачно", "Меркур", "Морава", "Пет", "Брза игра", "1240", "4 8 2 9 1 5", "waiting", "running") +
                listOf("stork", "stack", "waiting-for", "Нина", "spinner", "Mic", "Hourglass", "ThumbUp")
    }
}
