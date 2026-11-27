package io.ntole.kvizic.design.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.Metal
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The дукат, the game's coin, [size] across: a gold coin in the skin's own two golds, never tinted, its rim raised
 * and darker, a ring of beads inside the rim as on an old ducat, and the game's К, the app icon's letter, struck in
 * the middle. It only draws; put it beside a number for a price or a balance.
 */
@Composable
fun Ducat(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val gold = KvizicTheme.skin.metals.gold
    Spacer(modifier.size(size).drawBehind { drawDucat(gold) })
}

private fun DrawScope.drawDucat(gold: Metal) {
    val radius = size.minDimension / 2
    drawCircle(gold.shade, radius)
    val lip = radius * (1 - RIM / 2)
    drawArc(
        gold.face,
        startAngle = LIT_FROM,
        sweepAngle = LIT_SWEEP,
        useCenter = false,
        topLeft = center - Offset(lip, lip),
        size = Size(lip * 2, lip * 2),
        style = Stroke(radius * RIM * LIT_WIDTH, cap = StrokeCap.Round),
    )
    drawCircle(gold.face, radius * (1 - RIM))
    repeat(BEADS) { i ->
        val angle = i * 2 * PI / BEADS
        val at = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius * BEAD_RING
        drawCircle(gold.shade, radius * BEAD, at)
    }
    val scale = radius * LETTER / LETTER_HEIGHT
    withTransform({
        translate(center.x - LETTER_X * scale, center.y - LETTER_Y * scale)
        scale(scale, scale, pivot = Offset.Zero)
    }) { drawPath(Letter, gold.shade) }
}

private val Letter: Path by lazy { PathParser().parsePathString(LETTER_PATH).toPath() }

private const val LETTER_PATH =
    "M45.1 87Q41.79 87 40.01 85.17Q38.23 83.34 38.23 80.03V40Q38.23 36.61 40.01 34.83Q41.79 33.06 45.1 33.06" +
        "Q48.41 33.06 50.16 34.83Q51.92 36.61 51.92 40V54.75H58.27L56.31 56.12L67.98 36.56Q69.36 34.29 71.31 33.5" +
        "Q73.26 32.72 75.18 33.16Q77.09 33.6 78.46 35Q79.84 36.4 80.07 38.43Q80.3 40.47 78.78 42.87L68.54 58.93" +
        "L63.05 55.27Q68.28 56.25 71.75 59.01Q75.22 61.77 77.8 67.07L82.77 77.26Q84.12 80.03 83.64 82.14" +
        "Q83.16 84.25 81.53 85.49Q79.9 86.73 77.8 86.95Q75.71 87.17 73.76 86.15Q71.81 85.13 70.7 82.71L65.62 71.56" +
        "Q64.21 68.49 62.14 67.2Q60.07 65.92 56.78 65.92H51.92V80.03Q51.92 83.34 50.2 85.17Q48.48 87 45.1 87Z"
private const val LETTER_X = 61.2f
private const val LETTER_Y = 60.03f
private const val LETTER_HEIGHT = 53.94f
private const val LETTER = 0.9f

private const val RIM = 0.16f
private const val LIT_FROM = 160f
private const val LIT_SWEEP = 110f
private const val LIT_WIDTH = 0.45f
private const val BEADS = 20
private const val BEAD_RING = 0.7f
private const val BEAD = 0.05f
