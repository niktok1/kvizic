package io.ntole.kvizic.design.skins.buzzers

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ntole.kvizic.design.component.AnswerTileState
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.PanelKind
import io.ntole.kvizic.design.skin.AvatarPart
import io.ntole.kvizic.design.skin.BurstPart
import io.ntole.kvizic.design.skin.ButtonPart
import io.ntole.kvizic.design.skin.ChipPart
import io.ntole.kvizic.design.skin.ContrastPair
import io.ntole.kvizic.design.skin.ContrastUse
import io.ntole.kvizic.design.skin.FlapGlyph
import io.ntole.kvizic.design.skin.FlapPart
import io.ntole.kvizic.design.skin.LogoPart
import io.ntole.kvizic.design.skin.PanelPart
import io.ntole.kvizic.design.skin.PodiumPart
import io.ntole.kvizic.design.skin.SkinParts
import io.ntole.kvizic.design.skin.SpinnerPart
import io.ntole.kvizic.design.skin.SurfaceDecor
import io.ntole.kvizic.design.skin.SurfaceLook
import io.ntole.kvizic.design.skin.TilePart
import io.ntole.kvizic.design.skin.TimerMoment
import io.ntole.kvizic.design.skin.TimerPart
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** How Buzzers draws each component: enamel plates edged in ink, glass bulbs, split flaps. */
internal object BuzzersParts : SkinParts {
    private val colors = BuzzersColors
    private val space = BuzzersSpace
    private val shapes = BuzzersShapes
    private val depth = BuzzersDepth

    override val tile: TilePart = BuzzersTile

    override val button: ButtonPart =
        object : ButtonPart {
            override fun surface(
                kind: ButtonKind,
                size: ButtonSize,
                enabled: Boolean,
            ): SurfaceLook {
                val shape = if (size == ButtonSize.HERO) shapes.heroButton else shapes.button
                // The hero stands a little taller than the rest: the one to hit.
                val height = if (size == ButtonSize.HERO) depth.lift + space.xxs else depth.lift
                if (!enabled && kind != ButtonKind.QUIET) {
                    return plate(colors.unlit, colors.unlitSide, shape, height = height, decor = DarkSheen)
                }
                return when (kind) {
                    ButtonKind.PRIMARY -> {
                        plate(colors.primary, colors.primarySide, shape, height = height)
                    }

                    ButtonKind.SECONDARY -> {
                        plate(colors.plate, colors.plateSide, shape, height = height)
                    }

                    ButtonKind.DARK -> {
                        plate(colors.raised, colors.raisedSide, shape, height = height, decor = DarkSheen)
                    }

                    // A word alone, which only sinks a little under the finger.
                    ButtonKind.QUIET -> {
                        SurfaceLook(
                            fill = Color.Transparent,
                            side = Color.Transparent,
                            outline = Color.Transparent,
                            shape = shape,
                            height = space.xxs,
                            rest = space.xxs,
                            pressed = 0.dp,
                        )
                    }
                }
            }

            override fun content(
                kind: ButtonKind,
                enabled: Boolean,
            ): Color =
                when {
                    !enabled && kind == ButtonKind.QUIET -> colors.onPageMuted
                    !enabled -> colors.onUnlit
                    kind == ButtonKind.PRIMARY -> colors.onPrimary
                    kind == ButtonKind.SECONDARY -> colors.onPlate
                    kind == ButtonKind.DARK -> colors.onRaised
                    else -> colors.onPage
                }

            override fun supporting(
                kind: ButtonKind,
                enabled: Boolean,
            ): Color =
                when {
                    !enabled -> colors.onUnlit
                    kind == ButtonKind.PRIMARY -> colors.onPrimary
                    kind == ButtonKind.SECONDARY -> colors.onPlateMuted
                    kind == ButtonKind.DARK -> colors.onRaisedMuted
                    else -> colors.onPageMuted
                }
        }

    override val panel: PanelPart =
        object : PanelPart {
            private val raised = depth.lift - space.xxs

            override fun surface(kind: PanelKind): SurfaceLook =
                when (kind) {
                    PanelKind.PLAIN -> {
                        fixed(colors.raised, colors.raisedSide, shapes.panel, raised, DarkSheen)
                    }

                    PanelKind.OWN -> {
                        fixed(colors.raised, colors.raisedSide, shapes.panel, raised, DarkSheen)
                            .copy(outline = colors.primary)
                    }

                    PanelKind.SCREEN -> {
                        fixed(ScreenFace, colors.raisedSide, shapes.panel, raised, ScreenRule)
                    }

                    PanelKind.WELL -> {
                        fixed(WellFace, Color.Transparent, shapes.panel, 0.dp, WellShade)
                    }

                    PanelKind.EMPTY -> {
                        SurfaceLook(
                            fill = EmptySeat,
                            side = Color.Transparent,
                            outline = Color.Transparent,
                            shape = shapes.seat,
                            height = 0.dp,
                            rest = 0.dp,
                            pressed = 0.dp,
                            decor = DashedEdge,
                        )
                    }
                }

            override fun content(kind: PanelKind): Color =
                if (kind ==
                    PanelKind.EMPTY
                ) {
                    colors.onPageMuted
                } else {
                    colors.onRaised
                }

            override fun muted(kind: PanelKind): Color =
                if (kind ==
                    PanelKind.EMPTY
                ) {
                    colors.onPageMuted
                } else {
                    colors.onRaisedMuted
                }
        }

    override val chip: ChipPart =
        object : ChipPart {
            override fun surface(
                tone: ChipTone,
                selected: Boolean,
            ): SurfaceLook {
                val (fill, side) =
                    when {
                        selected || tone == ChipTone.ACCENT -> colors.primary to colors.primarySide
                        tone == ChipTone.GAIN -> colors.correct to colors.correctSide
                        tone == ChipTone.LOSS -> colors.wrong to colors.wrongSide
                        else -> colors.raised to colors.raisedSide
                    }
                return SurfaceLook(
                    fill = fill,
                    side = side,
                    outline = Ink,
                    shape = shapes.chip,
                    height = space.xxs,
                    rest = space.xxs,
                    pressed = 0.dp,
                )
            }

            override fun content(
                tone: ChipTone,
                selected: Boolean,
            ): Color = if (tone == ChipTone.NEUTRAL && !selected) colors.onRaised else Ink
        }

    override val flap: FlapPart =
        object : FlapPart {
            override val digit: Color = colors.onFlap

            override fun DrawScope.drawCell(
                from: FlapGlyph?,
                to: FlapGlyph?,
                turn: Float,
            ) = drawSplitFlap(from, to, turn, size)
        }

    override val timer: TimerPart =
        object : TimerPart {
            override val digit: Color = colors.onFlap

            override fun DrawScope.drawTimer(moment: TimerMoment) = drawMarquee(moment)
        }

    override val avatar: AvatarPart =
        object : AvatarPart {
            private val ink = 1.5.dp

            override val hostIcon: Color = Ink
            override val levelText: Color = colors.onPlate
            override val hostCrown: Color = Amber
            override val artInset: Dp = space.avatar.ring + ink

            override fun DrawScope.drawUnder(
                seat: Color,
                dimmed: Boolean,
            ) {
                val radius = size.minDimension / 2
                drawCircle(Ink, radius)
                drawCircle(if (dimmed) colors.bulbOff else seat, radius - ink.toPx())
                drawCircle(BuzzersAvatars.disc, radius - ink.toPx() - space.avatar.ring.toPx())
            }

            override fun DrawScope.drawOver(
                seat: Color,
                dimmed: Boolean,
            ) {
                if (dimmed) return
                // The ring's gloss: a short arc of light at its top left, as on an enamel pin.
                val radius = size.minDimension / 2 - ink.toPx() - space.avatar.ring.toPx() / 2
                drawArc(
                    color = Color.White.copy(alpha = 0.4f),
                    startAngle = 200f,
                    sweepAngle = 50f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = space.avatar.ring.toPx() / 2, cap = StrokeCap.Round),
                )
            }

            override fun DrawScope.drawHostBadge() {
                val radius = size.minDimension / 2
                drawCircle(Ink, radius)
                drawCircle(Amber, radius - ink.toPx())
            }

            override fun DrawScope.drawLevelBadge() {
                val radius = size.minDimension / 2
                drawCircle(Ink, radius)
                drawCircle(colors.plate, radius - ink.toPx())
            }
        }

    override val podium: PodiumPart =
        object : PodiumPart {
            override fun surface(place: Int): SurfaceLook =
                when (place) {
                    1 -> plate(colors.primary, colors.primarySide, shapes.podium, pressed = depth.lift)
                    2 -> plate(colors.plate, colors.plateSide, shapes.podium, pressed = depth.lift)
                    else -> plate(Copper, CopperSide, shapes.podium, pressed = depth.lift)
                }

            override fun content(place: Int): Color = Ink
        }

    override val spinner: SpinnerPart =
        object : SpinnerPart {
            override fun DrawScope.drawSpinner(turn: Float) {
                val bulbs = 8
                val bulb = size.minDimension * 0.2f
                val ring = size.minDimension / 2 - bulb * 0.6f
                // A chase: the head lit, a tail fading behind it, the rest dark.
                val head = floor(turn * bulbs).toInt()
                for (i in 0 until bulbs) {
                    val angle = (i.toFloat() / bulbs * 2 * PI - PI / 2).toFloat()
                    val at = center + Offset(cos(angle) * ring, sin(angle) * ring)
                    val age = (head - i).mod(bulbs)
                    drawMarqueeBulb(at, bulb, (1f - age / 4f).coerceIn(0f, 1f), colors.bulbOn)
                }
            }
        }

    override val burst: BurstPart =
        object : BurstPart {
            override val icon: Color = Ink

            override fun DrawScope.drawBurst(progress: Float) {
                val points = 12
                val outer = size.minDimension / 2 - space.strokeThin.toPx()
                val star =
                    Path().apply {
                        for (i in 0 until points * 2) {
                            val radius = if (i % 2 == 0) outer else outer * 0.74f
                            val angle = (i.toFloat() / (points * 2) * 2 * PI).toFloat()
                            val x = center.x + cos(angle) * radius
                            val y = center.y + sin(angle) * radius
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                // A comic burst, turning a little as it rises.
                rotate(progress * 24f) {
                    drawPath(star, Amber)
                    drawPath(star, Ink, style = Stroke(width = space.strokeThin.toPx(), join = StrokeJoin.Round))
                }
            }
        }

    override val logo: LogoPart =
        object : LogoPart {
            override val text: Color = Amber

            override fun DrawScope.drawSign(glow: (bulb: Int, of: Int) -> Float) =
                drawMarqueeSign(space.logo.signBulbsAcross, glow)
        }

    override fun contrastPairs(): List<ContrastPair> =
        listOf(
            ContrastPair("a result's mark on its stamp", Ink, Cream, ContrastUse.GRAPHIC),
            ContrastPair("the question on its screen", colors.onRaised, ScreenFace, ContrastUse.TEXT),
            ContrastPair("a muted word on the question's screen", colors.onRaisedMuted, ScreenFace, ContrastUse.TEXT),
            ContrastPair("a code's digit in its well", colors.onFlap, WellFace, ContrastUse.TEXT),
            ContrastPair("a word in the well", colors.onRaised, WellFace, ContrastUse.TEXT),
            ContrastPair("an empty seat's word", colors.onPageMuted, EmptySeat.over(Stage), ContrastUse.TEXT),
            ContrastPair("the third step's number", Ink, Copper, ContrastUse.TEXT),
            ContrastPair("a reaction on its burst", Ink, Amber, ContrastUse.GRAPHIC),
            ContrastPair("the host's microphone on its badge", Ink, Amber, ContrastUse.GRAPHIC),
            ContrastPair("the game's name on its sign", Amber, colors.raised, ContrastUse.TEXT),
            ContrastPair("the game's name on its sign's panel", Amber, ScreenFace, ContrastUse.TEXT),
            ContrastPair("a flap's digit on its lower half", colors.onFlap, colors.flapShade, ContrastUse.TEXT),
            ContrastPair("a lit bulb on the stage", colors.bulbOn, Stage, ContrastUse.GRAPHIC),
            ContrastPair("a warning bulb on the stage", colors.bulbWarn, Stage, ContrastUse.GRAPHIC),
            ContrastPair("a lit bulb on the timer's plate", colors.bulbOn, colors.raised, ContrastUse.GRAPHIC),
        )

    /** A raised plate in [fill] over its [side], edged in ink and glossed. */
    internal fun plate(
        fill: Color,
        side: Color,
        shape: Shape = shapes.tile,
        rest: Dp = depth.lift,
        height: Dp = depth.lift,
        pressed: Dp = depth.pressedLift,
        glow: Color = Color.Unspecified,
        decor: SurfaceDecor = Sheen,
    ): SurfaceLook =
        SurfaceLook(
            fill = fill,
            side = side,
            outline = Ink,
            shape = shape,
            height = height,
            rest = rest,
            pressed = pressed,
            glow = glow,
            decor = decor,
        )

    /** A surface that stands where it is: a panel, which nothing presses. */
    private fun fixed(
        fill: Color,
        side: Color,
        shape: Shape,
        height: Dp,
        decor: SurfaceDecor,
    ): SurfaceLook =
        SurfaceLook(fill, side, Ink, shape, height = height, rest = height, pressed = height, decor = decor)

    override fun toString(): String = "BuzzersParts"
}

/** Buzzers' answer tile: an enamel buzzer with a glass bulb for its letter. */
private object BuzzersTile : TilePart {
    private val colors = BuzzersColors
    private val space = BuzzersSpace
    private val depth = BuzzersDepth

    override fun surface(
        state: AnswerTileState,
        index: Int,
    ): SurfaceLook =
        when (state) {
            AnswerTileState.IDLE -> {
                BuzzersParts.plate(colors.plate, colors.plateSide, decor = SignSheen)
            }

            // Hit and held down, lit amber: a buzzer that has buzzed.
            AnswerTileState.LOCKED_IN -> {
                BuzzersParts.plate(
                    colors.lockedIn,
                    colors.lockedInSide,
                    rest = depth.lockedLift,
                    glow = Amber.copy(alpha = 0.3f),
                    decor = SignSheen,
                )
            }

            // Back up and lit, glowing: the one the host points to.
            AnswerTileState.CORRECT -> {
                BuzzersParts.plate(
                    colors.correct,
                    colors.correctSide,
                    glow = Green.copy(alpha = 0.5f),
                    decor = SignSheen,
                )
            }

            AnswerTileState.WRONG -> {
                BuzzersParts.plate(colors.wrong, colors.wrongSide, rest = depth.lockedLift, decor = SignSheen)
            }

            // Its light gone out: dark enamel, barely a gloss.
            AnswerTileState.DIMMED -> {
                BuzzersParts.plate(colors.unlit, colors.unlitSide, decor = DarkSignSheen)
            }
        }

    override fun content(state: AnswerTileState): Color =
        when (state) {
            AnswerTileState.IDLE -> colors.onPlate
            AnswerTileState.LOCKED_IN -> colors.onLockedIn
            AnswerTileState.CORRECT -> colors.onCorrect
            AnswerTileState.WRONG -> colors.onWrong
            AnswerTileState.DIMMED -> colors.onUnlit
        }

    override fun letterColor(
        state: AnswerTileState,
        index: Int,
    ): Color = if (state == AnswerTileState.DIMMED) colors.onLetterUnlit else colors.onLetter

    override fun markColor(
        state: AnswerTileState,
        index: Int,
    ): Color = if (state == AnswerTileState.DIMMED) colors.letterUnlit else colors.letter(index)

    override fun DrawScope.drawLetterMark(
        state: AnswerTileState,
        index: Int,
        lit: Float,
        color: Color,
    ) {
        val edge = space.strokeThin.toPx()
        val radius = size.minDimension / 2
        if (lit > 0f) {
            val reach = radius * 1.9f
            drawCircle(
                Brush.radialGradient(
                    0f to color.copy(alpha = 0.6f * lit),
                    1f to Color.Transparent,
                    center = center,
                    radius = reach,
                ),
                radius = reach,
            )
        }
        drawCircle(color, radius - edge / 2)
        // A lit bulb's filament, warm in its middle.
        if (lit > 0f) drawCircle(colors.bulbOn.copy(alpha = 0.45f * lit), radius * 0.5f)
        drawCircle(Ink, radius - edge / 2, style = Stroke(width = edge))
        drawGlint(center, radius, 0.4f)
    }

    override fun DrawScope.drawStamp(
        state: AnswerTileState,
        shown: Float,
    ) {
        if (shown <= 0f || (state != AnswerTileState.CORRECT && state != AnswerTileState.WRONG)) return
        // A round stamp over the bulb's lower corner, a tick or a cross in it, popping in.
        val radius = space.tile.stamp.toPx() / 2 * shown
        val at = Offset(size.width * 0.92f, size.height * 0.92f)
        drawCircle(Ink, radius + space.strokeThin.toPx(), at)
        drawCircle(Cream, radius, at)
        val mark =
            Path().apply {
                if (state == AnswerTileState.CORRECT) {
                    moveTo(at.x - radius * 0.46f, at.y)
                    lineTo(at.x - radius * 0.12f, at.y + radius * 0.36f)
                    lineTo(at.x + radius * 0.5f, at.y - radius * 0.36f)
                } else {
                    moveTo(at.x - radius * 0.36f, at.y - radius * 0.36f)
                    lineTo(at.x + radius * 0.36f, at.y + radius * 0.36f)
                    moveTo(at.x + radius * 0.36f, at.y - radius * 0.36f)
                    lineTo(at.x - radius * 0.36f, at.y + radius * 0.36f)
                }
            }
        drawPath(mark, Ink, style = Stroke(width = radius * 0.26f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * A split-flap cell of [cell]'s size: the upper half a shade lighter than the lower, the digit printed
 * across both, and the split between. While it turns, a flap falls about the split: the old digit's
 * upper half folding down over the new one's, then the new one's lower half coming down over the old
 * one's, each darker as it turns edge-on to the light.
 */
internal fun DrawScope.drawSplitFlap(
    from: FlapGlyph?,
    to: FlapGlyph?,
    turn: Float,
    cell: Size,
) {
    val colors = BuzzersColors
    val radius = CornerRadius(cell.minDimension * 0.14f)
    val half = cell.height / 2
    // The edge and the split thin with the cell, so a small score's digits are not cut in two.
    val edge = (cell.height * 0.035f).coerceIn(1f, BuzzersSpace.strokeThin.toPx() * 0.75f)
    val face = Size(cell.width - edge * 2, half - edge)
    drawRoundRect(Ink, size = cell, cornerRadius = radius)
    drawRoundRect(colors.flap, Offset(edge, edge), face, radius)
    drawRoundRect(colors.flapShade, Offset(edge, half), face, radius)
    val turning = turn < 1f && from !== to
    // At rest, the new digit's upper half shows above, and below it the old one's until the flap lands.
    clipRect(right = cell.width, bottom = half) { glyph(to, cell) }
    clipRect(top = half, right = cell.width, bottom = cell.height) { glyph(if (turning) from else to, cell) }
    if (turning) {
        val falling = turn < 0.5f
        val fold = (if (falling) cos(turn * PI) else -cos(turn * PI)).toFloat().coerceAtLeast(0.001f)
        withTransform({ scale(scaleX = 1f, scaleY = fold, pivot = Offset(cell.width / 2, half)) }) {
            val top = if (falling) 0f else half
            clipRect(top = top, right = cell.width, bottom = top + half) {
                drawRoundRect(
                    if (falling) colors.flap else colors.flapShade,
                    Offset(edge, if (falling) edge else half),
                    face,
                    radius,
                )
                glyph(if (falling) from else to, cell)
                drawRect(Ink.copy(alpha = 0.45f * (1f - fold)), Offset(0f, top), Size(cell.width, half))
            }
        }
    }
    // The split, and the pins it turns on at either side.
    drawRect(colors.flapSplit.copy(alpha = 0.85f), Offset(0f, half - edge / 2), Size(cell.width, edge))
    val pin = Size(cell.width * 0.07f, cell.height * 0.1f)
    drawRect(Ink, Offset(0f, half - pin.height / 2), pin)
    drawRect(Ink, Offset(cell.width - pin.width, half - pin.height / 2), pin)
}

/** [glyph] printed in the middle of a cell of [cell]'s size, its figures centred. */
private fun DrawScope.glyph(
    glyph: FlapGlyph?,
    cell: Size,
) {
    if (glyph == null) return
    with(glyph) { drawCentred(cell) }
}

/**
 * The timer: a dark plate ringed by bulbs, lit clockwise from the top as the lights come up while the
 * question is read, and going out anticlockwise as the time runs, the last lit one flickering out; the
 * seconds on a split flap in the middle. In the last seconds the bulbs still lit burn warning red.
 */
private fun DrawScope.drawMarquee(moment: TimerMoment) {
    val colors = BuzzersColors
    val whole = size.minDimension
    val bulbs = BuzzersSpace.timer.bulbs
    val bulb = whole * BuzzersSpace.timer.bulbFraction
    val ring = whole / 2 - bulb * 1.05f
    val stroke = BuzzersSpace.stroke.toPx()
    val plate = whole / 2 - stroke / 2
    // The plate the bulbs are set in, on its side as every raised thing on the set is.
    drawCircle(colors.raisedSide, plate, center + Offset(0f, BuzzersDepth.lift.toPx() / 2))
    drawCircle(Ink, plate, center + Offset(0f, BuzzersDepth.lift.toPx() / 2), style = Stroke(width = stroke))
    drawCircle(colors.raised, plate)
    drawCircle(Ink, plate, style = Stroke(width = stroke))
    val dial = ring - bulb * 1.05f
    drawCircle(ScreenFace, dial)
    drawCircle(Ink, dial, style = Stroke(width = stroke / 2))
    val litExact = moment.lit.coerceIn(0f, 1f) * bulbs
    val lit = kotlin.math.ceil(litExact).toInt()
    val life = litExact - floor(litExact)
    val lamp = if (moment.warn) colors.bulbWarn else colors.bulbOn
    for (i in 0 until bulbs) {
        val angle = (i.toFloat() / bulbs * 2 * PI - PI / 2).toFloat()
        val at = center + Offset(cos(angle) * ring, sin(angle) * ring)
        val glow =
            when {
                i >= lit -> 0f

                // The last lit: coming up with the light as the question is read, flickering out as time runs.
                i == lit - 1 && moment.filling && life > 0f -> life

                i == lit - 1 && !moment.filling && life > 0f && life < FLICKER_SHARE -> flicker(life / FLICKER_SHARE)

                else -> 1f
            }
        drawMarqueeBulb(at, bulb, glow, lamp)
    }
    val seconds = moment.seconds ?: return
    // One card for two digits, whatever the number, so it does not change size as the seconds run down.
    val card = Size(dial * 1.4f, dial * 1.2f)
    translate((size.width - card.width) / 2, (size.height - card.height) / 2) {
        drawSplitFlap(moment.previousSeconds ?: seconds, seconds, moment.turn, card)
    }
}

/** A flicker as a bulb goes out, [t] from 0 to 1 of it: bright and dim by turns, dark at its end. */
private fun flicker(t: Float): Float = ((1f - t) * (0.6f + 0.4f * sin(t * 5f * PI).toFloat())).coerceIn(0f, 1f)

private const val FLICKER_SHARE = 0.3f

/** A marquee bulb of [diameter] at [at], [glow] of the way lit, from dark glass to [lamp], with its halo. */
internal fun DrawScope.drawMarqueeBulb(
    at: Offset,
    diameter: Float,
    glow: Float,
    lamp: Color,
) {
    val radius = diameter / 2
    if (glow > 0f) {
        val reach = radius * 2.1f
        val halo =
            Brush.radialGradient(
                0f to lamp.copy(alpha = 0.55f * glow),
                1f to Color.Transparent,
                center = at,
                radius = reach,
            )
        drawCircle(halo, reach, at)
    }
    drawCircle(BuzzersColors.bulbOff, radius, at)
    if (glow > 0f) drawCircle(lamp.copy(alpha = glow), radius, at)
    drawCircle(Ink, radius, at, style = Stroke(width = radius * 0.22f))
    drawGlint(at, radius, 0.2f + 0.35f * glow)
}

/** A glass's glint, a small light at [at]'s upper left. */
private fun DrawScope.drawGlint(
    at: Offset,
    radius: Float,
    alpha: Float,
) {
    drawCircle(Color.White.copy(alpha = alpha), radius * 0.28f, at + Offset(-radius * 0.32f, -radius * 0.32f))
}

/**
 * The game's name's sign, over this scope: a dark board on its side, edged in ink, a panel inside it with
 * an amber rule, and a track of lit bulbs round its edge, [across] along the top and the bottom and as
 * many down each side as keeps them as far apart, each as bright as [glow] says by its place on the track.
 */
private fun DrawScope.drawMarqueeSign(
    across: Int,
    glow: (bulb: Int, of: Int) -> Float,
) {
    val space = BuzzersSpace
    val stroke = space.stroke.toPx()
    val lift = BuzzersDepth.lift.toPx()
    val board = Size(size.width - stroke, size.height - lift - stroke)
    val corner = CornerRadius(space.xl.toPx())
    translate(stroke / 2, stroke / 2) {
        drawRoundRect(BuzzersColors.raisedSide, Offset(0f, lift), board, corner)
        drawRoundRect(Ink, Offset(0f, lift), board, corner, style = Stroke(width = stroke))
        drawRoundRect(BuzzersColors.raised, Offset.Zero, board, corner)
        drawRoundRect(Ink, Offset.Zero, board, corner, style = Stroke(width = stroke))
        val track = space.lg.toPx()
        val inset = space.xxl.toPx()
        val inner = Size(board.width - inset * 2, board.height - inset * 2)
        drawRoundRect(ScreenFace, Offset(inset, inset), inner, CornerRadius(space.md.toPx()))
        drawRoundRect(
            Amber.copy(alpha = 0.35f),
            Offset(inset, inset),
            inner,
            CornerRadius(space.md.toPx()),
            style = Stroke(width = space.strokeThin.toPx() / 2),
        )
        val bulb = track * 0.62f
        val step = (board.width - track * 2) / (across - 1)
        val down = ((board.height - track * 2) / step).toInt().coerceAtLeast(1)
        val stepDown = (board.height - track * 2) / down
        val count = across * 2 + (down - 1) * 2
        var n = 0
        for (i in 0 until across) {
            val x = track + i * step
            drawMarqueeBulb(Offset(x, track), bulb, glow(n++, count), BuzzersColors.bulbOn)
            drawMarqueeBulb(Offset(x, board.height - track), bulb, glow(n++, count), BuzzersColors.bulbOn)
        }
        for (j in 1 until down) {
            val y = track + j * stepDown
            drawMarqueeBulb(Offset(track, y), bulb, glow(n++, count), BuzzersColors.bulbOn)
            drawMarqueeBulb(Offset(board.width - track, y), bulb, glow(n++, count), BuzzersColors.bulbOn)
        }
    }
}
