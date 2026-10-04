package io.ntole.kvizic.design.skins.notebook

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
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
import io.ntole.kvizic.design.skin.wobbled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * How Notebook draws each component: paper cards with wobbly ballpoint edges, marker highlights where
 * the stage has lights, a scribbled tick or cross for a result, and digits that roll rather than flap.
 */
internal object NotebookParts : SkinParts {
    private val colors = NotebookColors
    private val space = NotebookSpace
    private val shapes = NotebookShapes
    private val depth = NotebookDepth

    override val tile: TilePart =
        object : TilePart {
            override fun surface(
                state: AnswerTileState,
                index: Int,
            ): SurfaceLook =
                when (state) {
                    AnswerTileState.IDLE -> {
                        card(seed = index)
                    }

                    // Not a light but a highlighter's swipe across the answer, and the card pressed flat.
                    AnswerTileState.LOCKED_IN -> {
                        card(seed = index, rest = depth.lockedLift, decor = HighlighterSwipe(Highlighter))
                    }

                    AnswerTileState.CORRECT -> {
                        card(seed = index, decor = HighlighterSwipe(GreenHighlighter))
                    }

                    AnswerTileState.WRONG -> {
                        card(seed = index, rest = depth.lockedLift, decor = StruckThrough)
                    }

                    AnswerTileState.DIMMED -> {
                        card(seed = index, fill = colors.unlit, side = colors.unlitSide, outline = colors.onUnlit)
                    }
                }

            override fun content(state: AnswerTileState): Color =
                if (state == AnswerTileState.DIMMED) colors.onUnlit else colors.onPlate

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
                // The letter circled in marker, the circle filled in harder once it counts.
                val ring = circleWobble(size.minDimension / 2 - space.strokeThin.toPx(), index)
                drawPath(ring, color.copy(alpha = 0.35f + 0.5f * lit))
                drawPath(ring, color, style = Stroke(width = space.strokeThin.toPx() * 1.2f, cap = StrokeCap.Round))
            }

            override fun DrawScope.drawStamp(
                state: AnswerTileState,
                shown: Float,
            ) {
                if (shown <= 0f || (state != AnswerTileState.CORRECT && state != AnswerTileState.WRONG)) return
                // A tick or a cross scribbled over the letter, reaching past it.
                val reach = size.minDimension * 0.62f
                val pen = Stroke(width = space.stroke.toPx() * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val c = center
                val mark =
                    Path().apply {
                        if (state == AnswerTileState.CORRECT) {
                            moveTo(c.x - reach * 0.55f, c.y + reach * 0.05f)
                            quadraticTo(
                                c.x - reach * 0.2f,
                                c.y + reach * 0.35f,
                                c.x - reach * 0.1f,
                                c.y + reach * 0.55f,
                            )
                            quadraticTo(
                                c.x + reach * 0.2f,
                                c.y - reach * 0.2f,
                                c.x + reach * 0.75f,
                                c.y - reach * 0.65f,
                            )
                        } else {
                            moveTo(c.x - reach * 0.5f, c.y - reach * 0.5f)
                            quadraticTo(c.x, c.y + reach * 0.05f, c.x + reach * 0.55f, c.y + reach * 0.55f)
                            moveTo(c.x + reach * 0.5f, c.y - reach * 0.55f)
                            quadraticTo(c.x + reach * 0.05f, c.y, c.x - reach * 0.55f, c.y + reach * 0.5f)
                        }
                    }
                val pen2 = if (state == AnswerTileState.CORRECT) colors.gain else colors.loss
                drawPath(mark, pen2.copy(alpha = shown), style = pen)
            }
        }

    override val button: ButtonPart =
        object : ButtonPart {
            override fun surface(
                kind: ButtonKind,
                size: ButtonSize,
                enabled: Boolean,
            ): SurfaceLook {
                val shape = if (size == ButtonSize.HERO) shapes.heroButton else shapes.button
                val seed = kind.ordinal * 13 + size.ordinal
                if (!enabled) return card(seed, colors.unlit, colors.unlitSide, colors.onUnlit, shape = shape)
                return when (kind) {
                    ButtonKind.PRIMARY -> {
                        card(seed, colors.primary, colors.primarySide, shape = shape, decor = MarkerStrokes)
                    }

                    ButtonKind.SECONDARY -> {
                        card(seed, shape = shape)
                    }

                    ButtonKind.DARK -> {
                        card(seed, Color(0xFFE9ECF3), shape = shape)
                    }

                    ButtonKind.QUIET -> {
                        SurfaceLook(
                            fill = Color.Transparent,
                            side = Color.Transparent,
                            outline = Color.Transparent,
                            shape = shape,
                            height = space.xxs,
                            rest = space.xxs,
                            pressed = 0.dp,
                            decor = Underline,
                        )
                    }
                }
            }

            override fun content(
                kind: ButtonKind,
                enabled: Boolean,
            ): Color =
                when {
                    !enabled -> colors.onUnlit
                    kind == ButtonKind.PRIMARY -> colors.onPrimary
                    else -> colors.onPlate
                }

            override fun supporting(
                kind: ButtonKind,
                enabled: Boolean,
            ): Color =
                when {
                    !enabled -> colors.onUnlit
                    kind == ButtonKind.PRIMARY -> colors.onPrimary
                    else -> colors.onPlateMuted
                }
        }

    override val panel: PanelPart =
        object : PanelPart {
            override fun surface(kind: PanelKind): SurfaceLook =
                when (kind) {
                    PanelKind.PLAIN -> {
                        card(seed = 3, height = depth.lift - space.xxs, pressedToo = true, decor = Tape)
                    }

                    PanelKind.OWN -> {
                        card(
                            seed = 3,
                            outline = colors.onPageAccent,
                            height = depth.lift - space.xxs,
                            pressedToo = true,
                            decor = Tape,
                        )
                    }

                    PanelKind.SCREEN -> {
                        card(seed = 5, height = depth.lift - space.xxs, pressedToo = true, decor = RuledLines)
                    }

                    PanelKind.WELL -> {
                        card(seed = 7, fill = Color(0xFFF1F3F8), height = 0.dp, pressedToo = true)
                    }

                    PanelKind.EMPTY -> {
                        SurfaceLook(
                            fill = Color.Transparent,
                            side = Color.Transparent,
                            outline = Color.Transparent,
                            shape = shapes.seat,
                            height = 0.dp,
                            rest = 0.dp,
                            pressed = 0.dp,
                            decor = PencilDashes,
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

            override fun muted(kind: PanelKind): Color = colors.onRaisedMuted
        }

    override val chip: ChipPart =
        object : ChipPart {
            override fun surface(
                tone: ChipTone,
                selected: Boolean,
            ): SurfaceLook {
                val decor =
                    when {
                        selected || tone == ChipTone.ACCENT -> HighlighterSwipe(Highlighter)
                        tone == ChipTone.GAIN -> HighlighterSwipe(GreenHighlighter)
                        tone == ChipTone.LOSS -> HighlighterSwipe(PinkHighlighter)
                        else -> SurfaceDecor.None
                    }
                return card(seed = tone.ordinal, shape = shapes.chip, height = space.xxs, decor = decor)
            }

            override fun content(
                tone: ChipTone,
                selected: Boolean,
            ): Color = colors.onPlate
        }

    override val flap: FlapPart =
        object : FlapPart {
            override val digit: Color = colors.onFlap

            override fun DrawScope.drawCell(
                from: FlapGlyph?,
                to: FlapGlyph?,
                turn: Float,
            ) = drawRollingCell(from, to, turn, size)
        }

    override val timer: TimerPart =
        object : TimerPart {
            override val digit: Color = colors.onFlap

            override fun DrawScope.drawTimer(moment: TimerMoment) {
                // A clock sketched in ballpoint, the time left hatched in as a wedge that shrinks.
                val radius = size.minDimension / 2 - space.stroke.toPx()
                val face = circleWobble(radius, 11)
                drawPath(face, Card)
                val sweep = 360f * moment.lit.coerceIn(0f, 1f)
                val wedge = if (moment.warn) colors.bulbWarn else colors.bulbOn
                drawArc(
                    wedge.copy(alpha = 0.55f),
                    startAngle = -90f,
                    sweepAngle = if (moment.filling) sweep else -sweep,
                    useCenter = true,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                )
                val ticks = space.timer.bulbs
                for (i in 0 until ticks) {
                    val angle = (i.toFloat() / ticks * 2 * PI - PI / 2).toFloat()
                    val outer = center + Offset(cos(angle) * radius, sin(angle) * radius)
                    val inner = center + Offset(cos(angle) * radius * 0.84f, sin(angle) * radius * 0.84f)
                    drawLine(Ballpoint, inner, outer, strokeWidth = space.strokeThin.toPx(), cap = StrokeCap.Round)
                }
                drawPath(face, Ballpoint, style = Stroke(width = space.stroke.toPx(), cap = StrokeCap.Round))
                val seconds = moment.seconds ?: return
                val card = Size(radius * 1.2f, radius * 0.9f)
                translate(center.x - card.width / 2, center.y - card.height / 2) {
                    drawRollingCell(moment.previousSeconds ?: seconds, seconds, moment.turn, card, boxed = false)
                }
            }
        }

    override val avatar: AvatarPart =
        object : AvatarPart {
            override val hostIcon: Color = Ballpoint
            override val levelText: Color = colors.onPlate
            override val hostCrown: Color = Ballpoint
            override val artInset: Dp = space.avatar.ring + space.xxs

            override fun DrawScope.drawUnder(
                seat: Color,
                dimmed: Boolean,
            ) {
                drawPath(circleWobble(size.minDimension / 2 - space.strokeThin.toPx(), seat.hashCode()), Card)
            }

            override fun DrawScope.drawOver(
                seat: Color,
                dimmed: Boolean,
            ) {
                // A marker ring in the seat's colour, drawn round the animal twice, as a hand would.
                val ring = size.minDimension / 2 - space.avatar.ring.toPx() / 2 - space.xxs.toPx() / 2
                val marker = if (dimmed) colors.bulbOff else seat
                val pen = Stroke(width = space.avatar.ring.toPx(), cap = StrokeCap.Round)
                drawPath(circleWobble(ring, seat.hashCode()), marker, style = pen)
            }

            override fun DrawScope.drawHostBadge() {
                val radius = size.minDimension / 2
                drawPath(circleWobble(radius - space.strokeThin.toPx(), 17), StickyNote)
                drawPath(
                    circleWobble(radius - space.strokeThin.toPx(), 17),
                    Ballpoint,
                    style = Stroke(space.strokeThin.toPx()),
                )
            }

            override fun DrawScope.drawLevelBadge() {
                val radius = size.minDimension / 2
                drawPath(circleWobble(radius - space.strokeThin.toPx(), 17), colors.plate)
                drawPath(
                    circleWobble(radius - space.strokeThin.toPx(), 17),
                    Ballpoint,
                    style = Stroke(space.strokeThin.toPx()),
                )
            }
        }

    override val podium: PodiumPart =
        object : PodiumPart {
            override fun surface(place: Int): SurfaceLook =
                card(
                    seed = place,
                    shape = shapes.podium,
                    pressedToo = true,
                    decor = if (place == 1) HighlighterSwipe(Highlighter) else SurfaceDecor.None,
                )

            override fun content(place: Int): Color = Ballpoint
        }

    override val spinner: SpinnerPart =
        object : SpinnerPart {
            override fun DrawScope.drawSpinner(turn: Float) {
                // A pencil circling round and round, its line trailing.
                val radius = size.minDimension / 2 - space.stroke.toPx()
                rotate(turn * 360f) {
                    drawArc(
                        Ballpoint,
                        startAngle = 0f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = space.stroke.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }

    override val burst: BurstPart =
        object : BurstPart {
            override val icon: Color = Ballpoint

            override fun DrawScope.drawBurst(progress: Float) {
                // A scribbled star of highlighter behind the reaction.
                val outer = size.minDimension / 2 - space.strokeThin.toPx()
                val star =
                    Path().apply {
                        for (i in 0 until 10) {
                            val radius = if (i % 2 == 0) outer else outer * 0.55f
                            val angle = (i / 10f * 2 * PI - PI / 2).toFloat()
                            val x = center.x + cos(angle) * radius
                            val y = center.y + sin(angle) * radius
                            if (i == 0) moveTo(x, y) else lineTo(x, y)
                        }
                        close()
                    }
                rotate(progress * -20f) {
                    drawPath(star, Highlighter)
                    drawPath(star, Ballpoint, style = Stroke(width = space.strokeThin.toPx(), join = StrokeJoin.Round))
                }
            }
        }

    override val logo: LogoPart =
        object : LogoPart {
            override val text: Color = Ballpoint

            override fun DrawScope.drawSign(glow: (bulb: Int, of: Int) -> Float) {
                // A sticky note, with no lights to flicker, a little askew, its tape across the top.
                val note = Size(size.width * 0.86f, size.height * 0.92f)
                val at = Offset((size.width - note.width) / 2, (size.height - note.height) / 2)
                rotate(-2.5f) {
                    val outline = Outline.Rounded(RoundRect(Rect(at, note), CornerRadius(space.xs.toPx())))
                    val edge = outline.wobbled(space.xxs.toPx() / 2, 23)
                    translate(space.xs.toPx(), space.xs.toPx()) { drawPath(edge, Pencil.copy(alpha = 0.5f)) }
                    drawPath(edge, StickyNote)
                    drawPath(edge, Ballpoint, style = Stroke(width = space.strokeThin.toPx()))
                    val tape = Size(note.width * 0.3f, space.lg.toPx())
                    drawRect(
                        Color(0x99F5F1E1),
                        Offset(at.x + (note.width - tape.width) / 2, at.y - tape.height / 2),
                        tape,
                    )
                }
            }
        }

    override fun contrastPairs(): List<ContrastPair> =
        listOf(
            ContrastPair("an answer under a yellow swipe", Ballpoint, Highlighter.over(Card, SWIPE), ContrastUse.TEXT),
            ContrastPair(
                "an answer under a green swipe",
                Ballpoint,
                GreenHighlighter.over(Card, SWIPE),
                ContrastUse.TEXT,
            ),
            ContrastPair(
                "an answer under a pink swipe",
                Ballpoint,
                PinkHighlighter.over(Card, SWIPE),
                ContrastUse.TEXT,
            ),
            ContrastPair(
                "a lit letter in its circle",
                Ballpoint,
                Color(0xFF7CA7F2).over(Card, 0.85f),
                ContrastUse.TEXT,
            ),
            ContrastPair("a right answer's tick", NotebookColors.gain, Card, ContrastUse.GRAPHIC),
            ContrastPair("a wrong answer's cross", NotebookColors.loss, Card, ContrastUse.GRAPHIC),
            ContrastPair("the game's name on its note", Ballpoint, StickyNote, ContrastUse.TEXT),
            ContrastPair("a button that stands back", Ballpoint, Color(0xFFE9ECF3), ContrastUse.TEXT),
            ContrastPair("a word in the well", Ballpoint, Color(0xFFF1F3F8), ContrastUse.TEXT),
            ContrastPair("the host's microphone on its note", Ballpoint, StickyNote, ContrastUse.GRAPHIC),
            ContrastPair("a reaction on its star", Ballpoint, Highlighter, ContrastUse.GRAPHIC),
        )

    /** A paper card with a wobbly ballpoint edge over a pencilled shadow. */
    private fun card(
        seed: Int,
        fill: Color = Card,
        side: Color = Pencil,
        outline: Color = Ballpoint,
        shape: Shape = shapes.tile,
        height: Dp = depth.lift,
        rest: Dp = height,
        pressedToo: Boolean = false,
        decor: SurfaceDecor = SurfaceDecor.None,
    ): SurfaceLook =
        SurfaceLook(
            fill = fill,
            side = side,
            outline = outline,
            shape = shape,
            height = height,
            rest = rest,
            pressed = if (pressedToo) rest else depth.pressedLift,
            decor = decor,
            seed = seed,
        )

    /** A circle of [radius] about the scope's middle, as a hand draws one. */
    private fun DrawScope.circleWobble(
        radius: Float,
        seed: Int,
    ): Path {
        val box = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
        return Outline.Rounded(RoundRect(box, CornerRadius(radius))).wobbled(radius * 0.05f, seed)
    }

    override fun toString(): String = "NotebookParts"
}

/**
 * A digit cell of [cell]'s size, a box drawn on the page with the digit in it; while it turns, the old
 * digit rolls up and out as the new one rolls up in from below, an odometer's way rather than a flap's.
 */
private fun DrawScope.drawRollingCell(
    from: FlapGlyph?,
    to: FlapGlyph?,
    turn: Float,
    cell: Size,
    boxed: Boolean = true,
) {
    if (boxed) {
        drawRoundRect(Card, size = cell, cornerRadius = CornerRadius(cell.minDimension * 0.1f))
        drawRoundRect(
            GridLine,
            size = cell,
            cornerRadius = CornerRadius(cell.minDimension * 0.1f),
            style = Stroke(width = NotebookSpace.strokeThin.toPx()),
        )
    }
    clipRect(right = cell.width, bottom = cell.height) {
        if (turn >= 1f || from === to) {
            glyphAt(to, cell, 0f)
        } else {
            glyphAt(from, cell, -turn * cell.height)
            glyphAt(to, cell, (1f - turn) * cell.height)
        }
    }
}

private fun DrawScope.glyphAt(
    glyph: FlapGlyph?,
    cell: Size,
    dy: Float,
) {
    if (glyph == null) return
    with(glyph) { drawCentred(cell, dy) }
}

/** A highlighter's swipe across the face: a band of [ink], a little ragged at its ends. */
private data class HighlighterSwipe(
    val ink: Color,
) : SurfaceDecor {
    override fun DrawScope.decorate(
        size: Size,
        outline: Outline,
    ) {
        val band = size.height * 0.62f
        val top = (size.height - band) / 2
        rotate(-1.5f) {
            drawRoundRect(
                ink.copy(alpha = SWIPE),
                Offset(size.width * 0.03f, top),
                Size(size.width * 0.94f, band),
                CornerRadius(band * 0.2f),
            )
        }
    }
}

/** A wrong answer: struck through in red pen, over a pink swipe. */
private object StruckThrough : SurfaceDecor {
    private val swipe = HighlighterSwipe(PinkHighlighter)

    override fun DrawScope.decorate(
        size: Size,
        outline: Outline,
    ) {
        with(swipe) { decorate(size, outline) }
        val y = size.height * 0.55f
        drawLine(
            RedPen,
            Offset(size.width * 0.08f, y + size.height * 0.04f),
            Offset(size.width * 0.92f, y - size.height * 0.04f),
            strokeWidth = NotebookSpace.stroke.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** A marker's fill, its strokes still showing: a few lighter lines across it. */
private object MarkerStrokes : SurfaceDecor {
    override fun DrawScope.decorate(
        size: Size,
        outline: Outline,
    ) {
        val gap = NotebookSpace.md.toPx()
        var y = gap / 2
        while (y < size.height) {
            drawLine(
                Color.White.copy(alpha = 0.1f),
                Offset(0f, y),
                Offset(size.width, y - gap / 3),
                strokeWidth =
                    gap / 3,
            )
            y += gap
        }
    }
}

/** A strip of tape across a card's top, holding it in the book. */
private object Tape : SurfaceDecor {
    override fun DrawScope.decorate(
        size: Size,
        outline: Outline,
    ) {
        val tape = Size(size.width * 0.28f, NotebookSpace.lg.toPx())
        rotate(-3f, pivot = Offset(size.width / 2, 0f)) {
            drawRect(Color(0x99F5F1E1), Offset((size.width - tape.width) / 2, -tape.height / 2), tape)
        }
    }
}

/** A card of the exercise book's own ruled paper, for the question to be written on. */
private object RuledLines : SurfaceDecor {
    override fun DrawScope.decorate(
        size: Size,
        outline: Outline,
    ) {
        val gap = NotebookSpace.xl.toPx()
        var y = gap
        while (y < size.height - NotebookSpace.sm.toPx()) {
            drawLine(GridLine, Offset(NotebookSpace.sm.toPx(), y), Offset(size.width - NotebookSpace.sm.toPx(), y))
            y += gap
        }
    }
}

/** A quiet button's word, underlined in pen. */
private object Underline : SurfaceDecor {
    override fun DrawScope.decorate(
        size: Size,
        outline: Outline,
    ) {
        val y = size.height - NotebookSpace.sm.toPx()
        drawLine(
            Ballpoint,
            Offset(size.width * 0.2f, y),
            Offset(size.width * 0.8f, y - NotebookSpace.xxs.toPx()),
            strokeWidth = NotebookSpace.strokeThin.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/** An empty place, dashed in pencil. */
private object PencilDashes : SurfaceDecor {
    override fun DrawScope.decorate(
        size: Size,
        outline: Outline,
    ) {
        val dash = NotebookSpace.sm.toPx()
        drawPath(
            Path().apply { addOutline(outline) },
            Pencil,
            style =
                Stroke(
                    width = NotebookSpace.strokeThin.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.7f)),
                    cap = StrokeCap.Round,
                ),
        )
    }
}

/** How strong a highlighter's swipe lies on paper. */
private const val SWIPE = 0.8f

/** [this] at [alpha] as it shows over [below]. */
private fun Color.over(
    below: Color,
    alpha: Float,
): Color =
    Color(
        red = red * alpha + below.red * (1 - alpha),
        green = green * alpha + below.green * (1 - alpha),
        blue = blue * alpha + below.blue * (1 - alpha),
    )
