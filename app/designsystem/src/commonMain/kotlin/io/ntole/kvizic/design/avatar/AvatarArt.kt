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
 * All sixteen of the game's are drawn; any other id, a newer server's, draws as [SILHOUETTE].
 */
object AvatarArt {
    const val FOX: String = "fox"
    const val OWL: String = "owl"
    const val HEDGEHOG: String = "hedgehog"
    const val BEAR: String = "bear"
    const val WOLF: String = "wolf"
    const val LYNX: String = "lynx"
    const val DEER: String = "deer"
    const val STORK: String = "stork"
    const val SQUIRREL: String = "squirrel"
    const val HARE: String = "hare"
    const val BADGER: String = "badger"
    const val OTTER: String = "otter"
    const val TORTOISE: String = "tortoise"
    const val FROG: String = "frog"
    const val BEE: String = "bee"
    const val CAT: String = "cat"

    /** What any avatar this build has no drawing for draws as: a head, two ears, no face. */
    const val SILHOUETTE: String = "silhouette"

    /** The avatars this build draws, beside the silhouette. */
    val DRAWN: List<String> =
        listOf(
            FOX,
            BEAR,
            OWL,
            HEDGEHOG,
            WOLF,
            LYNX,
            DEER,
            STORK,
            SQUIRREL,
            HARE,
            BADGER,
            OTTER,
            TORTOISE,
            FROG,
            BEE,
            CAT,
        )

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
                WOLF -> wolf(palette)
                LYNX -> lynx(palette)
                DEER -> deer(palette)
                STORK -> stork(palette)
                SQUIRREL -> squirrel(palette)
                HARE -> hare(palette)
                BADGER -> badger(palette)
                OTTER -> otter(palette)
                TORTOISE -> tortoise(palette)
                FROG -> frog(palette)
                BEE -> bee(palette)
                CAT -> cat(palette)
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

    private fun wolf(p: AvatarPalette): ImageVector =
        art("wolf") {
            shape(p.grey, p.ink) { triangle(8f, 21f, 11.5f, 3f, 22f, 13f) }
            shape(p.greyDark) { triangle(12f, 15.5f, 13f, 8f, 17.8f, 12.6f) }
            shape(p.grey, p.ink) { triangle(40f, 21f, 36.5f, 3f, 26f, 13f) }
            shape(p.greyDark) { triangle(36f, 15.5f, 35f, 8f, 30.2f, 12.6f) }
            shape(p.grey) { foxHead() }
            // The pale muzzle and cheeks, and the dark of the brow running down to the nose.
            shape(p.cream) {
                moveTo(9f, 27f)
                curveTo(13f, 29.5f, 18f, 29.5f, 24f, 31f)
                curveTo(30f, 29.5f, 35f, 29.5f, 39f, 27f)
                curveTo(37f, 31f, 33f, 34f, 30f, 36f)
                curveTo(28f, 38f, 26f, 41f, 24f, 43f)
                curveTo(22f, 41f, 20f, 38f, 18f, 36f)
                curveTo(15f, 34f, 11f, 31f, 9f, 27f)
                close()
            }
            shape(p.greyDark) { triangle(20.5f, 12f, 27.5f, 12f, 24f, 27f) }
            line(p.ink, LINE) { foxHead() }
            listOf(MIDDLE - 7.5f, MIDDLE + 7.5f).forEach { x ->
                shape(p.gold) { circle(x, 22f, 2.6f) }
                shape(p.ink) { circle(x, 22f, 1.4f) }
            }
            shape(p.ink) {
                moveTo(21.2f, 37.6f)
                curveTo(21.2f, 36f, 26.8f, 36f, 26.8f, 37.6f)
                curveTo(26.8f, 39.4f, 25f, 41f, 24f, 41f)
                curveTo(23f, 41f, 21.2f, 39.4f, 21.2f, 37.6f)
                close()
            }
        }

    private fun lynx(p: AvatarPalette): ImageVector =
        art("lynx") {
            // The ears and their black tufts, and the ruff of the cheeks behind the head.
            shape(p.tan, p.ink) { triangle(9f, 19f, 12.5f, 5f, 21f, 12.5f) }
            shape(p.tan, p.ink) { triangle(39f, 19f, 35.5f, 5f, 27f, 12.5f) }
            line(p.ink, LINE) {
                moveTo(12.5f, 5f)
                lineTo(11.5f, 0.8f)
                moveTo(35.5f, 5f)
                lineTo(36.5f, 0.8f)
            }
            shape(p.tan, p.ink) { triangle(4f, 33f, 10f, 24f, 13f, 37f) }
            shape(p.tan, p.ink) { triangle(44f, 33f, 38f, 24f, 35f, 37f) }
            shape(p.tan, p.ink) { ellipse(24f, 26.5f, 16f, 15.5f) }
            listOf(19f, 24f, 29f).forEach { x -> shape(p.tanDark) { circle(x, 15.5f, 1.3f) } }
            shape(p.cream, p.ink, width = THIN) { ellipse(24f, 34f, 7.5f, 5.6f) }
            eyes(p, y = 24.5f, apart = 6.8f, radius = 2.3f)
            shape(p.blush, p.ink, width = THIN) { triangle(21.6f, 30.6f, 26.4f, 30.6f, 24f, 33.4f) }
            line(p.ink, THIN) {
                moveTo(24f, 33.4f)
                verticalLineTo(35.4f)
                moveTo(21f, 36.4f)
                quadTo(24f, 38.4f, 27f, 36.4f)
            }
        }

    private fun deer(p: AvatarPalette): ImageVector =
        art("deer") {
            // The antlers, each a beam and two tines.
            line(p.tanDark, ANTLER) {
                moveTo(18f, 12.5f)
                lineTo(13f, 2.5f)
                moveTo(15.2f, 7f)
                lineTo(9.5f, 5.5f)
                moveTo(30f, 12.5f)
                lineTo(35f, 2.5f)
                moveTo(32.8f, 7f)
                lineTo(38.5f, 5.5f)
            }
            shape(p.brown, p.ink) { ellipse(9.5f, 19f, 5.5f, 3.2f) }
            shape(p.tan) { ellipse(9.5f, 19f, 3f, 1.6f) }
            shape(p.brown, p.ink) { ellipse(38.5f, 19f, 5.5f, 3.2f) }
            shape(p.tan) { ellipse(38.5f, 19f, 3f, 1.6f) }
            shape(p.brown, p.ink) { ellipse(24f, 27f, 13f, 17f) }
            shape(p.cream) { circle(20.5f, 15.5f, 1.2f) }
            shape(p.cream) { circle(27.5f, 15.5f, 1.2f) }
            shape(p.cream, p.ink, width = THIN) { ellipse(24f, 37.5f, 7.2f, 5.6f) }
            eyes(p, y = 25.5f, apart = 6f, radius = 2.3f)
            shape(p.ink) { ellipse(24f, 35f, 2.8f, 2f) }
        }

    private fun stork(p: AvatarPalette): ImageVector =
        art("stork") {
            // The black of the wings at the shoulders, the white head over them, and the long beak.
            shape(p.greyDark, p.ink) {
                moveTo(5f, 47f)
                curveTo(8f, 37f, 40f, 37f, 43f, 47f)
                close()
            }
            shape(p.cream, p.ink) { circle(24f, 24f, 15f) }
            eyes(p, y = 21.5f, apart = 6.5f, radius = 2.2f)
            shape(p.orange, p.ink) { triangle(20f, 26.5f, 28f, 26.5f, 24f, 46f) }
            line(p.orangeDark, THIN) {
                moveTo(24f, 28f)
                lineTo(24f, 42f)
            }
        }

    private fun squirrel(p: AvatarPalette): ImageVector =
        art("squirrel") {
            shape(p.orange, p.ink) { triangle(10.5f, 16f, 13f, 4.5f, 20.5f, 12f) }
            shape(p.orange, p.ink) { triangle(37.5f, 16f, 35f, 4.5f, 27.5f, 12f) }
            line(p.orangeDark, LINE) {
                moveTo(13f, 4.5f)
                lineTo(12f, 1f)
                moveTo(35f, 4.5f)
                lineTo(36f, 1f)
            }
            shape(p.orange, p.ink) { circle(24f, 27.5f, 16f) }
            shape(p.cream) { circle(17f, 33f, 5.2f) }
            shape(p.cream) { circle(31f, 33f, 5.2f) }
            eyes(p, y = 23.5f, apart = 6.5f, radius = 2.4f)
            shape(p.ink) { ellipse(24f, 29.6f, 2.3f, 1.7f) }
            // The two front teeth.
            shape(p.cream, p.ink, width = THIN) {
                moveTo(21.8f, 32.2f)
                lineTo(26.2f, 32.2f)
                lineTo(26.2f, 37f)
                lineTo(21.8f, 37f)
                close()
            }
            line(p.ink, THIN) {
                moveTo(24f, 32.2f)
                lineTo(24f, 37f)
            }
        }

    private fun hare(p: AvatarPalette): ImageVector =
        art("hare") {
            listOf(16.5f, 31.5f).forEach { x ->
                shape(p.grey, p.ink) { ellipse(x, 10.5f, 4.3f, 10.5f) }
                shape(p.blush) { ellipse(x, 11f, 2f, 7.2f) }
            }
            shape(p.grey, p.ink) { ellipse(24f, 31f, 15f, 14f) }
            shape(p.cream) { circle(21f, 36f, 4.2f) }
            shape(p.cream) { circle(27f, 36f, 4.2f) }
            eyes(p, y = 27.5f, apart = 6.5f, radius = 2.2f)
            shape(p.blush, p.ink, width = THIN) { triangle(21.8f, 32f, 26.2f, 32f, 24f, 34.6f) }
            shape(p.cream, p.ink, width = THIN) {
                moveTo(22.4f, 38.6f)
                lineTo(25.6f, 38.6f)
                lineTo(25.6f, 41.8f)
                lineTo(22.4f, 41.8f)
                close()
            }
        }

    private fun badger(p: AvatarPalette): ImageVector =
        art("badger") {
            shape(p.greyDark, p.ink) { circle(11f, 16f, 4.6f) }
            shape(p.cream) { circle(11f, 16f, 2.1f) }
            shape(p.greyDark, p.ink) { circle(37f, 16f, 4.6f) }
            shape(p.cream) { circle(37f, 16f, 2.1f) }
            shape(p.grey) { circle(24f, 27f, 16f) }
            // The white blaze down the middle, and the two black stripes through the eyes either side.
            shape(p.cream) {
                moveTo(20f, 11.2f)
                curveTo(21.5f, 10.8f, 26.5f, 10.8f, 28f, 11.2f)
                curveTo(29f, 20f, 29f, 30f, 26.4f, 38.5f)
                lineTo(21.6f, 38.5f)
                curveTo(19f, 30f, 19f, 20f, 20f, 11.2f)
                close()
            }
            shape(p.ink) {
                moveTo(13.4f, 15f)
                curveTo(15f, 13.4f, 17.6f, 12f, 20f, 11.2f)
                curveTo(19f, 20f, 19f, 30f, 21.6f, 38.5f)
                lineTo(19.2f, 38.2f)
                curveTo(14.5f, 32f, 12.4f, 22f, 13.4f, 15f)
                close()
            }
            shape(p.ink) {
                moveTo(34.6f, 15f)
                curveTo(33f, 13.4f, 30.4f, 12f, 28f, 11.2f)
                curveTo(29f, 20f, 29f, 30f, 26.4f, 38.5f)
                lineTo(28.8f, 38.2f)
                curveTo(33.5f, 32f, 35.6f, 22f, 34.6f, 15f)
                close()
            }
            line(p.ink, LINE) { circle(24f, 27f, 16f) }
            listOf(17.2f, 30.8f).forEach { x ->
                shape(p.cream) { circle(x, 24.5f, 2.3f) }
                shape(p.ink) { circle(x, 24.5f, 1.2f) }
            }
            shape(p.ink) { ellipse(24f, 38f, 3f, 2.3f) }
        }

    private fun otter(p: AvatarPalette): ImageVector =
        art("otter") {
            shape(p.brown, p.ink) { circle(11.5f, 17.5f, 3.6f) }
            shape(p.brown, p.ink) { circle(36.5f, 17.5f, 3.6f) }
            shape(p.brown, p.ink) { ellipse(24f, 27.5f, 17f, 15f) }
            shape(p.cream, p.ink, width = THIN) { ellipse(24f, 33.5f, 10f, 7f) }
            eyes(p, y = 24f, apart = 6.6f, radius = 2.3f)
            shape(p.ink) {
                moveTo(21f, 29.4f)
                curveTo(21f, 28f, 27f, 28f, 27f, 29.4f)
                curveTo(27f, 31f, 25f, 32.4f, 24f, 32.4f)
                curveTo(23f, 32.4f, 21f, 31f, 21f, 29.4f)
                close()
            }
            line(p.ink, THIN) {
                moveTo(24f, 32.4f)
                verticalLineTo(34.4f)
                moveTo(16f, 32.6f)
                lineTo(8.5f, 31.4f)
                moveTo(16f, 35f)
                lineTo(8.5f, 35.8f)
                moveTo(32f, 32.6f)
                lineTo(39.5f, 31.4f)
                moveTo(32f, 35f)
                lineTo(39.5f, 35.8f)
            }
        }

    private fun tortoise(p: AvatarPalette): ImageVector =
        art("tortoise") {
            // The shell's rim under the head, its plates marked, and the head out of it.
            shape(p.brown, p.ink) {
                moveTo(3f, 47f)
                curveTo(5f, 35.5f, 43f, 35.5f, 45f, 47f)
                close()
            }
            line(p.brownDark, THIN) {
                moveTo(14f, 39.5f)
                lineTo(16f, 47f)
                moveTo(24f, 38f)
                lineTo(24f, 47f)
                moveTo(34f, 39.5f)
                lineTo(32f, 47f)
            }
            shape(p.green, p.ink) { ellipse(24f, 24f, 12.5f, 14f) }
            shape(p.greenDark) { circle(19.5f, 14.5f, 1.4f) }
            shape(p.greenDark) { circle(28.5f, 14.5f, 1.4f) }
            eyes(p, y = 22f, apart = 5.2f, radius = 2.2f)
            shape(p.blush) { circle(16.6f, 28f, 1.9f) }
            shape(p.blush) { circle(31.4f, 28f, 1.9f) }
            shape(p.ink) { circle(22.6f, 27f, 0.8f) }
            shape(p.ink) { circle(25.4f, 27f, 0.8f) }
            line(p.ink, THIN) {
                moveTo(20f, 30.6f)
                quadTo(24f, 33.6f, 28f, 30.6f)
            }
        }

    private fun frog(p: AvatarPalette): ImageVector =
        art("frog") {
            // The two eyes' bumps, the wide head over their lower halves, and the eyes on top.
            shape(p.green, p.ink) { circle(14f, 15f, 7f) }
            shape(p.green, p.ink) { circle(34f, 15f, 7f) }
            shape(p.green, p.ink) { ellipse(24f, 30f, 20f, 13.5f) }
            listOf(14f, 34f).forEach { x ->
                shape(p.cream, p.ink, width = THIN) { circle(x, 15f, 4.4f) }
                shape(p.ink) { circle(x, 15.4f, 2.2f) }
                shape(p.cream) { circle(x + GLINT_X, 14.3f, GLINT) }
            }
            shape(p.greenDark) { circle(24f, 21.5f, 1.6f) }
            shape(p.blush) { circle(10.5f, 33f, 2.6f) }
            shape(p.blush) { circle(37.5f, 33f, 2.6f) }
            shape(p.ink) { circle(21.6f, 26.4f, 0.9f) }
            shape(p.ink) { circle(26.4f, 26.4f, 0.9f) }
            line(p.ink, LINE) {
                moveTo(12.5f, 31.5f)
                quadTo(24f, 40.5f, 35.5f, 31.5f)
            }
        }

    private fun bee(p: AvatarPalette): ImageVector =
        art("bee") {
            // The wings behind, the antennae, the head, and the stripe and dark of the head's foot.
            shape(p.cream, p.ink, width = THIN) { ellipse(9f, 15f, 6f, 7.5f) }
            shape(p.cream, p.ink, width = THIN) { ellipse(39f, 15f, 6f, 7.5f) }
            line(p.ink, THIN) {
                moveTo(19.5f, 12.5f)
                lineTo(15.5f, 3.5f)
                moveTo(28.5f, 12.5f)
                lineTo(32.5f, 3.5f)
            }
            shape(p.ink) { circle(15.5f, 3.5f, 1.9f) }
            shape(p.ink) { circle(32.5f, 3.5f, 1.9f) }
            shape(p.gold) { circle(24f, 27f, 16f) }
            shape(p.ink) {
                moveTo(10.8f, 35.6f)
                lineTo(37.2f, 35.6f)
                lineTo(34.8f, 38.8f)
                lineTo(13.2f, 38.8f)
                close()
            }
            shape(p.ink) {
                moveTo(15.6f, 40.6f)
                lineTo(32.4f, 40.6f)
                quadTo(24f, 46.2f, 15.6f, 40.6f)
                close()
            }
            line(p.ink, LINE) { circle(24f, 27f, 16f) }
            listOf(17.5f, 30.5f).forEach { x ->
                shape(p.ink) { ellipse(x, 24.5f, 3f, 3.6f) }
                shape(p.cream) { circle(x + 1.1f, 23.2f, 1.1f) }
            }
            shape(p.blush) { circle(13.6f, 30f, 2f) }
            shape(p.blush) { circle(34.4f, 30f, 2f) }
            line(p.ink, THIN) {
                moveTo(20.5f, 30.6f)
                quadTo(24f, 33.4f, 27.5f, 30.6f)
            }
        }

    private fun cat(p: AvatarPalette): ImageVector =
        art("cat") {
            shape(p.greyDark, p.ink) { triangle(8.5f, 22f, 10f, 5.5f, 21f, 14f) }
            shape(p.blush) { triangle(11.5f, 17.5f, 12f, 10f, 17.4f, 14.2f) }
            shape(p.greyDark, p.ink) { triangle(39.5f, 22f, 38f, 5.5f, 27f, 14f) }
            shape(p.blush) { triangle(36.5f, 17.5f, 36f, 10f, 30.6f, 14.2f) }
            shape(p.greyDark, p.ink) { ellipse(24f, 27.5f, 17f, 15f) }
            shape(p.cream, p.ink, width = THIN) { ellipse(24f, 34f, 7f, 5f) }
            listOf(MIDDLE - 7f, MIDDLE + 7f).forEach { x ->
                shape(p.gold) { ellipse(x, 24.5f, 2.9f, 3.1f) }
                shape(p.ink) { ellipse(x, 24.5f, 0.9f, 2.5f) }
            }
            shape(p.blush, p.ink, width = THIN) { triangle(21.8f, 30.6f, 26.2f, 30.6f, 24f, 33f) }
            line(p.ink, THIN) {
                moveTo(24f, 33f)
                verticalLineTo(34.6f)
                moveTo(21f, 36f)
                quadTo(22.6f, 37f, 24f, 34.6f)
                quadTo(25.4f, 37f, 27f, 36f)
            }
            line(p.cream, THIN) {
                moveTo(15.5f, 32f)
                lineTo(6.5f, 30.4f)
                moveTo(15.5f, 34.4f)
                lineTo(6.5f, 35.4f)
                moveTo(32.5f, 32f)
                lineTo(41.5f, 30.4f)
                moveTo(32.5f, 34.4f)
                lineTo(41.5f, 35.4f)
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
    private const val ANTLER = 3.2f
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
