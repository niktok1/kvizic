package io.ntole.kvizic.design.avatar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import io.ntole.kvizic.design.skin.AvatarPalette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The avatars, drawn by hand on a 48 by 48 grid: an animal's head, facing out, in flat colours under a
 * bold ink line, so each reads at the smallest size a lobby strip draws it. Each is built in a skin's
 * [AvatarPalette], so one set of drawings wears every skin.
 *
 * Four are drawn so far; any other id, a newer server's included, draws as [SILHOUETTE].
 */
object AvatarArt {
    const val FOX: String = "fox"
    const val OWL: String = "owl"
    const val HEDGEHOG: String = "hedgehog"
    const val BEAR: String = "bear"

    /** What any avatar this build has no drawing for draws as: a head, two ears, no face. */
    const val SILHOUETTE: String = "silhouette"

    /** The avatars this build draws, beside the silhouette. */
    val DRAWN: List<String> = listOf(FOX, OWL, HEDGEHOG, BEAR)

    /** Whether [id] has a drawing of its own here. */
    fun isDrawn(id: String): Boolean = id in DRAWN

    private val built = mutableMapOf<Pair<String, AvatarPalette>, ImageVector>()

    /** The drawing of [id] in [palette], built once for each pair. */
    fun of(
        id: String,
        palette: AvatarPalette,
    ): ImageVector {
        val key = (if (isDrawn(id)) id else SILHOUETTE) to palette
        return built.getOrPut(key) {
            when (key.first) {
                FOX -> fox(palette)
                OWL -> owl(palette)
                HEDGEHOG -> hedgehog(palette)
                BEAR -> bear(palette)
                else -> silhouette(palette)
            }
        }
    }

    private fun fox(p: AvatarPalette): ImageVector =
        art("fox") {
            // The ears, behind the head, each with its dark inside.
            shape(p.orange, p.ink) { triangle(8f, 21f, 11f, 3.5f, 22f, 13f) }
            shape(p.orangeDark) { triangle(11.8f, 15.5f, 12.8f, 8.5f, 17.5f, 12.6f) }
            shape(p.orange, p.ink) { triangle(40f, 21f, 37f, 3.5f, 26f, 13f) }
            shape(p.orangeDark) { triangle(36.2f, 15.5f, 35.2f, 8.5f, 30.5f, 12.6f) }
            // The head, wide at the cheeks and narrowing to the chin.
            shape(p.orange) { foxHead() }
            // The white of the cheeks and chin, up to a point between the eyes' line and the nose.
            shape(p.cream) {
                moveTo(5.4f, 22f)
                curveTo(9f, 27f, 14f, 28.4f, 19f, 28.4f)
                curveTo(21.5f, 28.4f, 23f, 30f, 24f, 33f)
                curveTo(25f, 30f, 26.5f, 28.4f, 29f, 28.4f)
                curveTo(34f, 28.4f, 39f, 27f, 42.6f, 22f)
                curveTo(41f, 28f, 37f, 31.5f, 33f, 34f)
                curveTo(29f, 37f, 26.5f, 41f, 24f, 43f)
                curveTo(21.5f, 41f, 19f, 37f, 15f, 34f)
                curveTo(11f, 31.5f, 7f, 28f, 5.4f, 22f)
                close()
            }
            line(p.ink, LINE) { foxHead() }
            eyes(p, y = 21.5f, apart = 7.5f, radius = 2.2f)
            shape(p.ink) {
                moveTo(21.4f, 38.4f)
                curveTo(21.4f, 37f, 26.6f, 37f, 26.6f, 38.4f)
                curveTo(26.6f, 40f, 25f, 41.6f, 24f, 41.6f)
                curveTo(23f, 41.6f, 21.4f, 40f, 21.4f, 38.4f)
                close()
            }
        }

    private fun PathBuilder.foxHead() {
        moveTo(5f, 19f)
        curveTo(5f, 13f, 13f, 10.5f, 24f, 10.5f)
        curveTo(35f, 10.5f, 43f, 13f, 43f, 19f)
        curveTo(43f, 25f, 39f, 30f, 33f, 34f)
        curveTo(29f, 37f, 26.5f, 41f, 24f, 43f)
        curveTo(21.5f, 41f, 19f, 37f, 15f, 34f)
        curveTo(9f, 30f, 5f, 25f, 5f, 19f)
        close()
    }

    private fun owl(p: AvatarPalette): ImageVector =
        art("owl") {
            // The body, its two ear tufts, and the tan of its feathers.
            shape(p.tan, p.ink) {
                moveTo(9f, 14f)
                lineTo(7f, 3.5f)
                lineTo(17f, 9.5f)
                curveTo(20f, 8.6f, 28f, 8.6f, 31f, 9.5f)
                lineTo(41f, 3.5f)
                lineTo(39f, 14f)
                curveTo(43f, 20f, 43f, 30f, 38.5f, 37f)
                curveTo(35f, 42.5f, 29.5f, 45f, 24f, 45f)
                curveTo(18.5f, 45f, 13f, 42.5f, 9.5f, 37f)
                curveTo(5f, 30f, 5f, 20f, 9f, 14f)
                close()
            }
            // The two discs of its face, each an eye in gold round a dark pupil.
            listOf(OWL_LEFT, OWL_RIGHT).forEach { x ->
                shape(p.cream, p.ink, width = THIN) { circle(x, OWL_EYES, OWL_DISC) }
                shape(p.gold) { circle(x, OWL_EYES, OWL_IRIS) }
                shape(p.ink) { circle(x, OWL_EYES, OWL_PUPIL) }
                shape(p.cream) { circle(x + GLINT_X, OWL_EYES - GLINT_Y, GLINT) }
            }
            // A frown of a brow over both, and the beak under.
            line(p.ink, LINE) {
                moveTo(10f, 13.2f)
                lineTo(24f, 18.2f)
                lineTo(38f, 13.2f)
            }
            shape(p.orange, p.ink, width = THIN) { triangle(21.6f, 27f, 26.4f, 27f, 24f, 32.8f) }
            // Three feathers on its chest.
            line(p.tanDark, THIN) {
                listOf(18f, 24f, 30f).forEach { x ->
                    moveTo(x - 2.2f, 37f)
                    lineTo(x, 39.2f)
                    lineTo(x + 2.2f, 37f)
                }
            }
        }

    private fun hedgehog(p: AvatarPalette): ImageVector =
        art("hedgehog") {
            // The spines, a crown of points round the top of the head.
            shape(p.greyDark, p.ink) { spines() }
            // The ears, then the face, a drop pointing down to the snout.
            shape(p.tanDark, p.ink, width = THIN) { circle(14f, 19.5f, 3.4f) }
            shape(p.tanDark, p.ink, width = THIN) { circle(34f, 19.5f, 3.4f) }
            shape(p.tan, p.ink) {
                moveTo(24f, 45f)
                curveTo(19f, 43f, 11f, 36f, 11f, 27.5f)
                curveTo(11f, 20f, 17f, 16f, 24f, 16f)
                curveTo(31f, 16f, 37f, 20f, 37f, 27.5f)
                curveTo(37f, 36f, 29f, 43f, 24f, 45f)
                close()
            }
            shape(p.blush) { circle(16.4f, 33f, 2.3f) }
            shape(p.blush) { circle(31.6f, 33f, 2.3f) }
            eyes(p, y = 27.5f, apart = 4.8f, radius = 2.1f)
            shape(p.ink) { circle(24f, 41.6f, 2.8f) }
        }

    /** A crown of [SPINES] points about the head's middle, from the left of its top round to the right. */
    private fun PathBuilder.spines() {
        val points = SPINES * 2
        for (i in 0..points) {
            val angle = (SPINE_FROM + (SPINE_TO - SPINE_FROM) * i / points) * PI / HALF_TURN
            val radius = if (i % 2 == 0) SPINE_INNER else SPINE_OUTER
            val x = SPINE_X + radius * cos(angle).toFloat()
            val y = SPINE_Y - radius * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    private fun bear(p: AvatarPalette): ImageVector =
        art("bear") {
            listOf(BEAR_EAR_LEFT, BEAR_EAR_RIGHT).forEach { x ->
                shape(p.brown, p.ink) { circle(x, BEAR_EAR_Y, BEAR_EAR) }
                shape(p.tanDark) { circle(x, BEAR_EAR_Y, BEAR_EAR_INSIDE) }
            }
            shape(p.brown, p.ink) { circle(24f, 26f, 17.5f) }
            shape(p.tan, p.ink, width = THIN) { ellipse(24f, 32.6f, 8.6f, 6.6f) }
            eyes(p, y = 22.8f, apart = 7f, radius = 2.2f)
            shape(p.ink) {
                moveTo(20.4f, 29.2f)
                curveTo(20.4f, 27.6f, 27.6f, 27.6f, 27.6f, 29.2f)
                curveTo(27.6f, 31.2f, 25.3f, 32.8f, 24f, 32.8f)
                curveTo(22.7f, 32.8f, 20.4f, 31.2f, 20.4f, 29.2f)
                close()
            }
            line(p.ink, THIN) {
                moveTo(24f, 33f)
                verticalLineTo(35.2f)
                moveTo(21f, 35.2f)
                quadTo(24f, 37.8f, 27f, 35.2f)
            }
        }

    private fun silhouette(p: AvatarPalette): ImageVector =
        art("silhouette") {
            shape(p.silhouette, p.ink) { circle(14f, 14f, 5.5f) }
            shape(p.silhouette, p.ink) { circle(34f, 14f, 5.5f) }
            shape(p.silhouette, p.ink) { circle(24f, 27f, 16.5f) }
            // A question mark where the face would be: an animal this build has not drawn yet.
            val faint = p.ink.copy(alpha = UNKNOWN_ALPHA)
            line(faint, LINE) {
                moveTo(20f, 23.4f)
                curveTo(20f, 20.6f, 21.9f, 19f, 24.1f, 19f)
                curveTo(26.3f, 19f, 28f, 20.6f, 28f, 22.7f)
                curveTo(28f, 25.6f, 24.1f, 26.2f, 24.1f, 29.6f)
            }
            shape(faint) { circle(24.1f, 34.2f, 1.7f) }
        }

    /** Two eyes [apart] either side of the middle at [y], each with a glint. */
    private fun ImageVector.Builder.eyes(
        p: AvatarPalette,
        y: Float,
        apart: Float,
        radius: Float,
    ) {
        listOf(MIDDLE - apart, MIDDLE + apart).forEach { x ->
            shape(p.ink) { ellipse(x, y, radius, radius * EYE_TALL) }
            shape(p.cream) { circle(x + radius * GLINT_SHARE, y - radius * GLINT_SHARE, radius * GLINT_SIZE) }
        }
    }

    private fun art(
        name: String,
        draw: ImageVector.Builder.() -> Unit,
    ): ImageVector =
        ImageVector
            .Builder(
                name = "avatar-$name",
                defaultWidth = GRID.dp,
                defaultHeight = GRID.dp,
                viewportWidth = GRID,
                viewportHeight = GRID,
            ).apply(draw)
            .build()

    /** A shape filled in [fill], outlined in [outline] if given. */
    private fun ImageVector.Builder.shape(
        fill: Color,
        outline: Color? = null,
        width: Float = LINE,
        draw: PathBuilder.() -> Unit,
    ) {
        path(
            fill = SolidColor(fill),
            stroke = outline?.let { SolidColor(it) },
            strokeLineWidth = if (outline != null) width else 0f,
            strokeLineJoin = StrokeJoin.Round,
            strokeLineCap = StrokeCap.Round,
            pathBuilder = draw,
        )
    }

    /** A line of [width] in [color], nothing filled. */
    private fun ImageVector.Builder.line(
        color: Color,
        width: Float,
        draw: PathBuilder.() -> Unit,
    ) {
        path(
            stroke = SolidColor(color),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }

    private fun PathBuilder.triangle(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
    ) {
        moveTo(x1, y1)
        lineTo(x2, y2)
        lineTo(x3, y3)
        close()
    }

    private fun PathBuilder.circle(
        x: Float,
        y: Float,
        radius: Float,
    ) = ellipse(x, y, radius, radius)

    private fun PathBuilder.ellipse(
        x: Float,
        y: Float,
        rx: Float,
        ry: Float,
    ) {
        moveTo(x - rx, y)
        arcTo(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = x + rx, y1 = y)
        arcTo(rx, ry, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = x - rx, y1 = y)
        close()
    }

    private const val GRID = 48f
    private const val MIDDLE = GRID / 2
    private const val LINE = 2.4f
    private const val THIN = 1.7f
    private const val HALF_TURN = 180.0
    private const val EYE_TALL = 1.2f
    private const val GLINT_SHARE = 0.35f
    private const val GLINT_SIZE = 0.36f
    private const val UNKNOWN_ALPHA = 0.45f

    private const val OWL_LEFT = 16.5f
    private const val OWL_RIGHT = 31.5f
    private const val OWL_EYES = 22f
    private const val OWL_DISC = 7.8f
    private const val OWL_IRIS = 4.9f
    private const val OWL_PUPIL = 2.6f
    private const val GLINT = 0.95f
    private const val GLINT_X = 1.1f
    private const val GLINT_Y = 1.2f

    private const val SPINES = 9
    private const val SPINE_FROM = 200.0
    private const val SPINE_TO = -20.0
    private const val SPINE_X = 24f
    private const val SPINE_Y = 28f
    private const val SPINE_INNER = 15f
    private const val SPINE_OUTER = 22f

    private const val BEAR_EAR_LEFT = 11.5f
    private const val BEAR_EAR_RIGHT = 36.5f
    private const val BEAR_EAR_Y = 12.5f
    private const val BEAR_EAR = 6.5f
    private const val BEAR_EAR_INSIDE = 3.3f
}
