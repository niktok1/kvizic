package io.ntole.kvizic.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.ntole.kvizic.design.component.AnswerTileState
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.PanelKind
import io.ntole.kvizic.design.skin.ContrastPair
import io.ntole.kvizic.design.skin.ContrastUse
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every colour pair a skin puts text or an icon on, in every skin, held to WCAG AA: 4.5 to 1 for text,
 * 3 to 1 for large text and for an icon or a thing's edge. The pairs are read from the skin as it draws:
 * its colour roles, what each part puts on each of its surfaces in every state, every colour of its
 * backdrop under the page's text, and the pairs its parts name for what they draw beyond those.
 */
class SkinContrastTest {
    @Test
    fun `every text and icon reads at AA in every skin`() {
        val failures =
            Skins.ALL.flatMap { skin ->
                pairsOf(skin).mapNotNull { pair ->
                    val ratio = contrast(pair.foreground, pair.background)
                    if (ratio >= pair.use.least) {
                        null
                    } else {
                        "${skin.id}: ${pair.what} reads at ${ratio.shown()} to 1, under ${pair.use.least} (${pair.use})"
                    }
                }
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `a skin names every pair it is held to`() {
        Skins.ALL.forEach { skin -> assertTrue(pairsOf(skin).size > MANY_PAIRS, "${skin.id} names too few pairs") }
    }

    @Test
    fun `every skin is its own and found by its id`() {
        assertEquals(
            Skins.ALL.size,
            Skins.ALL
                .map { it.id }
                .toSet()
                .size,
        )
        Skins.ALL.forEach { assertEquals(it, Skins.ofId(it.id)) }
        assertEquals(null, Skins.ofId("no such skin"))
        assertTrue(Skins.Default in Skins.ALL)
    }

    @Test
    fun `every skin seats a full room in colours of its own`() {
        Skins.ALL.forEach { skin ->
            assertTrue(skin.colors.seats.size >= ROOM, "${skin.id} has ${skin.colors.seats.size} seat colours")
            assertEquals(
                skin.colors.seats.size,
                skin.colors.seats
                    .toSet()
                    .size,
                "${skin.id} repeats a seat colour",
            )
        }
    }

    private fun pairsOf(skin: Skin): List<ContrastPair> {
        val c = skin.colors
        val parts = skin.parts
        val text = ContrastUse.TEXT
        val pairs = mutableListOf<ContrastPair>()

        fun pair(
            what: String,
            fg: Color,
            bg: Color,
            use: ContrastUse = text,
        ) {
            pairs += ContrastPair(what, fg, bg, use)
        }

        // The page's words, on the page and on every colour its art shows under them.
        val pageText =
            listOf(
                "the page's text" to c.onPage,
                "muted text" to c.onPageMuted,
                "an accent" to c.onPageAccent,
                "points won" to c.gain,
                "points lost" to c.loss,
            )
        (listOf(c.page) + skin.backdrop.colors).forEach { under ->
            pageText.forEach { (what, fg) -> pair("$what on the page at $under", fg, under) }
        }
        pair("text on a raised panel", c.onRaised, c.raised)
        pair("muted text on a raised panel", c.onRaisedMuted, c.raised)
        pair("text on a plate", c.onPlate, c.plate)
        pair("muted text on a plate", c.onPlateMuted, c.plate)
        pair("the main action's word", c.onPrimary, c.primary)
        pair("a locked answer", c.onLockedIn, c.lockedIn)
        pair("a right answer", c.onCorrect, c.correct)
        pair("a wrong answer", c.onWrong, c.wrong)
        pair("an answer gone dark", c.onUnlit, c.unlit)
        pair("a letter gone dark", c.onLetterUnlit, c.letterUnlit)
        pair("a flap's digit", c.onFlap, c.flap)
        c.letters.forEachIndexed { i, letter -> pair("letter $i on its mark", c.onLetter, letter) }
        c.seats.forEachIndexed { i, seat -> pair("seat $i's ring on the page", seat, c.page, ContrastUse.GRAPHIC) }
        pair("the focus ring on the page", c.focus, c.page, ContrastUse.GRAPHIC)

        // What each part puts on each of its surfaces, in every state it has.
        AnswerTileState.entries.forEach { state ->
            c.letters.indices.forEach { i ->
                val look = parts.tile.surface(state, i)
                val mark = parts.tile.markColor(state, i)
                pair("a tile's answer, $state", parts.tile.content(state), look.fill.solidOver(c.page))
                pair("a tile's letter, $state", parts.tile.letterColor(state, i), mark)
            }
            // A tile a player may tap stands out from the page by its face or by its outline.
            if (state == AnswerTileState.IDLE) {
                val look = parts.tile.surface(state, 0)
                val face = look.fill.solidOver(c.page)
                val line = look.outline.solidOver(c.page)
                val edge = if (contrast(face, c.page) >= contrast(line, c.page)) face else line
                pair("an idle tile's edge on the page", edge, c.page, ContrastUse.GRAPHIC)
            }
        }
        ButtonKind.entries.forEach { kind ->
            listOf(true, false).forEach { enabled ->
                val look = parts.button.surface(kind, ButtonSize.REGULAR, enabled)
                val under = look.fill.solidOver(c.page)
                pair("a $kind button's word, enabled $enabled", parts.button.content(kind, enabled), under)
                pair("a $kind button's second line, enabled $enabled", parts.button.supporting(kind, enabled), under)
            }
        }
        PanelKind.entries.forEach { kind ->
            val under =
                parts.panel
                    .surface(kind)
                    .fill
                    .solidOver(c.page)
            pair("a $kind panel's text", parts.panel.content(kind), under)
            pair("a $kind panel's muted text", parts.panel.muted(kind), under)
        }
        ChipTone.entries.forEach { tone ->
            listOf(true, false).forEach { selected ->
                val under =
                    parts.chip
                        .surface(tone, selected)
                        .fill
                        .solidOver(c.page)
                pair("a $tone chip's word, selected $selected", parts.chip.content(tone, selected), under)
            }
        }
        (1..PODIUM).forEach { place ->
            pair(
                "the podium's step $place",
                parts.podium.content(place),
                parts.podium
                    .surface(place)
                    .fill
                    .solidOver(c.page),
            )
        }
        pair("a flap's digit as its part sets it", parts.flap.digit, c.flap)
        pair("the timer's seconds as its part sets them", parts.timer.digit, c.flap)

        return pairs + parts.contrastPairs()
    }

    /** WCAG's contrast ratio of [a] and [b]: the lighter's luminance and 0.05, over the darker's and 0.05. */
    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val (la, lb) = a.luminance() to b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    /** A colour as it shows over [below]: itself where it is opaque, mixed in by its alpha where it is not. */
    private fun Color.solidOver(below: Color): Color =
        if (alpha >= 1f) {
            this
        } else {
            Color(
                red = red * alpha + below.red * (1 - alpha),
                green = green * alpha + below.green * (1 - alpha),
                blue = blue * alpha + below.blue * (1 - alpha),
            )
        }

    private fun Float.shown(): String = (kotlin.math.round(this * 100) / 100).toString()

    private companion object {
        const val MANY_PAIRS = 60
        const val ROOM = 8
        const val PODIUM = 3
    }
}
