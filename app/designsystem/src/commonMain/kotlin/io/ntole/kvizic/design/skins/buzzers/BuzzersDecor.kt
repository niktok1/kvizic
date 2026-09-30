package io.ntole.kvizic.design.skins.buzzers

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import io.ntole.kvizic.design.skin.SurfaceDecor

/**
 * Enamel's gloss: [highlight] falling from the face's top to nothing a little under its middle, a touch
 * of [shade] at its bottom, and a line of light along its top edge, where the overhead lamp catches it.
 * With a [rule], a fine line inset along the edge, as an enamel street sign has round its lettering.
 */
internal class EnamelSheen(
    private val highlight: Color,
    private val shade: Color,
    private val rule: Color = Color.Transparent,
) : SurfaceDecor {
    override fun DrawScope.decorate(
        size: Size,
        outline: Outline,
    ) {
        if (rule.alpha > 0f) {
            val inset = BuzzersSpace.sm.toPx()
            val inner = outline.insetBy(inset)
            if (inner !=
                null
            ) {
                drawOutline(inner, rule, style = Stroke(width = BuzzersSpace.strokeThin.toPx() * RULE_WIDTH))
            }
        }
        clipPath(Path().apply { addOutline(outline) }) {
            drawRect(
                Brush.verticalGradient(0f to highlight, 0.5f to Color.Transparent, 1f to shade, endY = size.height),
            )
            val inset = BuzzersSpace.xs.toPx() + BuzzersSpace.xxs.toPx()
            val reach = BuzzersSpace.lg.toPx()
            // Only along a face wide enough to take it: on a round button it would read as a notch.
            if (size.width < reach * SHEEN_LINE_ROOM) return@clipPath
            drawLine(
                highlight.copy(alpha = (highlight.alpha * 2.2f).coerceAtMost(1f)),
                Offset(reach, inset),
                Offset(size.width - reach, inset),
                strokeWidth = BuzzersSpace.strokeThin.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }

    override fun equals(other: Any?): Boolean =
        other is EnamelSheen && other.highlight == highlight && other.shade == shade && other.rule == rule

    override fun hashCode(): Int = (highlight.hashCode() * 31 + shade.hashCode()) * 31 + rule.hashCode()
}

/** The outline drawn [by] further in on every side, its corners with it, or null where nothing is left. */
private fun Outline.insetBy(by: Float): Outline? =
    when (this) {
        is Outline.Rectangle -> {
            val r = rect
            if (r.width <= by * 2 || r.height <= by * 2) null else Outline.Rectangle(r.deflate(by))
        }

        is Outline.Rounded -> {
            val r = roundRect
            if (r.width <= by * 2 || r.height <= by * 2) {
                null
            } else {
                Outline.Rounded(
                    RoundRect(
                        left = r.left + by,
                        top = r.top + by,
                        right = r.right - by,
                        bottom = r.bottom - by,
                        topLeftCornerRadius = r.topLeftCornerRadius.less(by),
                        topRightCornerRadius = r.topRightCornerRadius.less(by),
                        bottomRightCornerRadius = r.bottomRightCornerRadius.less(by),
                        bottomLeftCornerRadius = r.bottomLeftCornerRadius.less(by),
                    ),
                )
            }
        }

        is Outline.Generic -> {
            null
        }
    }

private fun CornerRadius.less(by: Float): CornerRadius =
    CornerRadius((x - by).coerceAtLeast(0f), (y - by).coerceAtLeast(0f))

/** How many times the sheen line's inset from each end a face must be wide for the line to be drawn. */
private const val SHEEN_LINE_ROOM = 6f

/** The inset rule's width, as a share of the thin stroke. */
private const val RULE_WIDTH = 0.75f

/** The gloss on every light plate. */
internal val Sheen = EnamelSheen(highlight = Color.White.copy(alpha = 0.32f), shade = Ink.copy(alpha = 0.1f))

/** An answer's plate: the gloss, and the rule of an enamel sign inset along its edge. */
internal val SignSheen =
    EnamelSheen(
        highlight = Color.White.copy(alpha = 0.32f),
        shade = Ink.copy(alpha = 0.1f),
        rule = Ink.copy(alpha = 0.2f),
    )

/** An answer's plate gone dark: the fainter gloss, and its rule in the stage's cream. */
internal val DarkSignSheen =
    EnamelSheen(highlight = Cream.copy(alpha = 0.07f), shade = Color.Transparent, rule = Cream.copy(alpha = 0.08f))

/** The fainter gloss on dark enamel: a panel, a button that stands back, a tile gone dark. */
internal val DarkSheen = EnamelSheen(highlight = Cream.copy(alpha = 0.07f), shade = Color.Transparent)

/** The question's screen: a fine cream rule inside its edge, as a monitor on the set has a bezel. */
internal object ScreenRule : SurfaceDecor {
    override fun DrawScope.decorate(
        size: Size,
        outline: Outline,
    ) {
        val inset = BuzzersSpace.sm.toPx()
        drawRoundRect(
            Cream.copy(alpha = 0.2f),
            Offset(inset, inset),
            Size(size.width - inset * 2, size.height - inset * 2),
            CornerRadius(BuzzersSpace.md.toPx()),
            style = Stroke(width = BuzzersSpace.strokeThin.toPx() / 2),
        )
    }
}

/** A well sunk in the stage: the dark falling in from its top edge. */
internal object WellShade : SurfaceDecor {
    override fun DrawScope.decorate(
        size: Size,
        outline: Outline,
    ) {
        clipPath(Path().apply { addOutline(outline) }) {
            drawRect(
                Brush.verticalGradient(
                    0f to Ink.copy(alpha = 0.7f),
                    1f to Color.Transparent,
                    endY = BuzzersSpace.lg.toPx(),
                ),
            )
        }
    }
}

/** An empty seat's edge: dashes in the page's muted colour, where a member will sit. */
internal object DashedEdge : SurfaceDecor {
    override fun DrawScope.decorate(
        size: Size,
        outline: Outline,
    ) {
        val dash = BuzzersSpace.sm.toPx()
        drawPath(
            Path().apply { addOutline(outline) },
            BuzzersColors.onPageMuted.copy(alpha = 0.55f),
            style =
                Stroke(
                    width = BuzzersSpace.strokeThin.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash * 0.8f)),
                    cap = StrokeCap.Round,
                ),
        )
    }
}

/** An empty seat's floor: the stage, lightened a breath. */
internal val EmptySeat = Cream.copy(alpha = 0.04f)

/** [this] translucent colour as it shows over [below]. */
internal fun Color.over(below: Color): Color =
    Color(
        red = red * alpha + below.red * (1 - alpha),
        green = green * alpha + below.green * (1 - alpha),
        blue = blue * alpha + below.blue * (1 - alpha),
    )
