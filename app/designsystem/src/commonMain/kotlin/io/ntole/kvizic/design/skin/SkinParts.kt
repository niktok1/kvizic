package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import io.ntole.kvizic.design.component.AnswerTileState
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.PanelKind

/**
 * How a skin draws each component. A component owns its layout, its semantics, its clicks and its touch
 * target, and asks its part only how to look: which surface to stand on and what to draw. Every drawing
 * here runs in the draw phase and is given its moment of motion as a number, so a skin cannot break the
 * motion rule: a frame of any animation draws again and composes nothing.
 */
@Immutable
interface SkinParts {
    val tile: TilePart
    val button: ButtonPart
    val panel: PanelPart
    val chip: ChipPart
    val flap: FlapPart
    val timer: TimerPart
    val avatar: AvatarPart
    val podium: PodiumPart
    val spinner: SpinnerPart
    val burst: BurstPart
    val logo: LogoPart

    /** Every colour pair these parts draw text or an icon in that the colour roles do not name by themselves. */
    fun contrastPairs(): List<ContrastPair> = emptyList()
}

interface TilePart {
    fun surface(
        state: AnswerTileState,
        index: Int,
    ): SurfaceLook

    /** The answer's text on the tile. */
    fun content(state: AnswerTileState): Color

    /** The letter, on its mark. */
    fun letterColor(
        state: AnswerTileState,
        index: Int,
    ): Color

    /** The colour of the mark the letter stands in: its bulb's glass, its marker's ink. */
    fun markColor(
        state: AnswerTileState,
        index: Int,
    ): Color

    /**
     * The mark the letter stands in, over this scope, in [color]: [markColor]'s for [state], or one on its
     * way there as the tile settles into it. [lit] from 0 at rest to 1 once the tile lights up.
     */
    fun DrawScope.drawLetterMark(
        state: AnswerTileState,
        index: Int,
        lit: Float,
        color: Color,
    )

    /**
     * What stamps the tile's result on it, over the letter's mark this scope covers and free to reach past
     * it, [shown] from 0 to 1 as it comes in: a tick, a cross. Nothing for a state with no result.
     */
    fun DrawScope.drawStamp(
        state: AnswerTileState,
        shown: Float,
    )
}

interface ButtonPart {
    /** The button's surface; a quiet button's is clear, so only its word shows, and sinks under the finger. */
    fun surface(
        kind: ButtonKind,
        size: ButtonSize,
        enabled: Boolean,
    ): SurfaceLook

    fun content(
        kind: ButtonKind,
        enabled: Boolean,
    ): Color

    /** A button's second line, under its word. */
    fun supporting(
        kind: ButtonKind,
        enabled: Boolean,
    ): Color
}

interface PanelPart {
    fun surface(kind: PanelKind): SurfaceLook

    fun content(kind: PanelKind): Color

    fun muted(kind: PanelKind): Color
}

interface ChipPart {
    fun surface(
        tone: ChipTone,
        selected: Boolean,
    ): SurfaceLook

    fun content(
        tone: ChipTone,
        selected: Boolean,
    ): Color
}

/**
 * A glyph laid out to be drawn on a flap or a timer: its [layout], and how far down it the middle of its
 * figures stands, which centres it on its cell as the line's own box would not.
 */
class FlapGlyph(
    val layout: TextLayoutResult,
    val middle: Float,
) {
    /** Draws the glyph with its figures' middle [dy] below the middle of a cell of [cell]'s size. */
    fun DrawScope.drawCentred(
        cell: Size,
        dy: Float = 0f,
    ) {
        drawText(
            layout,
            topLeft = Offset((cell.width - layout.size.width) / 2, cell.height / 2 - middle + dy),
        )
    }
}

interface FlapPart {
    /**
     * One cell over this scope, turning from [from] to [to] at [turn], 0 before the turn starts and 1 once
     * it is done: a flap falling, a digit rolling. A null glyph is a blank cell.
     */
    fun DrawScope.drawCell(
        from: FlapGlyph?,
        to: FlapGlyph?,
        turn: Float,
    )

    /** The digits' colour, which the component sets them in. */
    val digit: Color
}

/** A timer's moment, as its part draws it. */
class TimerMoment(
    /**
     * How much of the timer is lit, from 0 to 1. With a skin's bulbs, the share of its bulbs lit, and
     * the fraction past a whole bulb how far through its life the last one is, to flicker it out.
     */
    val lit: Float,
    /** Whether the lights are coming up, the question still read alone, rather than going out. */
    val filling: Boolean,
    /** Whether it is in its last seconds. */
    val warn: Boolean,
    /** The seconds left, drawn, and the seconds before them, while [turn] runs from one to the other. */
    val seconds: FlapGlyph?,
    val previousSeconds: FlapGlyph?,
    val turn: Float,
)

interface TimerPart {
    fun DrawScope.drawTimer(moment: TimerMoment)

    /** The seconds' colour, which the component sets them in. */
    val digit: Color
}

interface AvatarPart {
    /** Under the animal, over this scope: the disc and the seat's ring. */
    fun DrawScope.drawUnder(
        seat: Color,
        dimmed: Boolean,
    )

    /** Over the animal: whatever frames it on top. */
    fun DrawScope.drawOver(
        seat: Color,
        dimmed: Boolean,
    )

    /** The badge of an order, over this scope, under the number drawn in [hostIcon]. */
    fun DrawScope.drawHostBadge()

    val hostIcon: Color

    /** The level's badge, over this scope, in the skin's secondary colours, under the number drawn in [levelText]. */
    fun DrawScope.drawLevelBadge()

    val levelText: Color

    /** The host's crown, worn above the avatar's head. */
    val hostCrown: Color

    /** How far in from the avatar's edge the animal is drawn, inside the frame, at any size. */
    val artInset: Dp
}

interface PodiumPart {
    /** The step of [place], 1 for the winner. */
    fun surface(place: Int): SurfaceLook

    fun content(place: Int): Color
}

interface SpinnerPart {
    /** The spinner over this scope, [turn] of the way round, from 0 to 1. */
    fun DrawScope.drawSpinner(turn: Float)
}

interface BurstPart {
    /** A reaction's burst over this scope, behind its icon, at [progress] from 0 to 1. */
    fun DrawScope.drawBurst(progress: Float)

    val icon: Color
}

interface LogoPart {
    /**
     * The sign the game's name stands on, over this scope. A skin whose sign has lights asks [glow] how
     * bright each is, from 0, out, to 1, by its place among them, `bulb` of `of`, so a few can flicker.
     */
    fun DrawScope.drawSign(glow: (bulb: Int, of: Int) -> Float = { _, _ -> 1f })

    val text: Color
}
