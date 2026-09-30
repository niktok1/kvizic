package io.ntole.kvizic.design.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Every icon the game draws, by hand on a 24 by 24 grid, so no icon library and no emoji is needed: a
 * few strokes each, one width, round ends, filled where a shape reads better solid at a small size.
 * They are black and carry no colour of their own; [KvizicIcon][io.ntole.kvizic.design.component.KvizicIcon]
 * tints each from the skin, so an icon follows the skin as text does.
 */
object KvizicIcons {
    /** A host's hand microphone, its ball head up and to the right: the room's host, the водитељ. */
    val Mic: ImageVector by lazy {
        icon("Mic") {
            // The handle, thick and tapering towards its end, then the collar where it meets the head.
            path(fill = SolidColor(Color.Black)) {
                moveTo(10.4f, 12.1f)
                lineTo(12.6f, 14.3f)
                lineTo(6.1f, 20.6f)
                curveTo(5.6f, 21.1f, 4.8f, 21.1f, 4.3f, 20.6f)
                curveTo(3.8f, 20.1f, 3.8f, 19.3f, 4.3f, 18.8f)
                close()
            }
            outline {
                // The ball head, a circle about (15, 9), and its grille.
                circle(x = 15f, y = 9f, radius = 5f)
                moveTo(11.6f, 7.6f)
                lineTo(16.4f, 12.4f)
                moveTo(13.6f, 5.6f)
                lineTo(18.4f, 10.4f)
            }
        }
    }

    val Check: ImageVector by lazy {
        icon("Check") {
            outline(width = BOLD) {
                moveTo(5f, 12.5f)
                lineTo(10f, 17.5f)
                lineTo(19.5f, 7f)
            }
        }
    }

    val Cross: ImageVector by lazy {
        icon("Cross") {
            outline(width = BOLD) {
                moveTo(6.5f, 6.5f)
                lineTo(17.5f, 17.5f)
                moveTo(17.5f, 6.5f)
                lineTo(6.5f, 17.5f)
            }
        }
    }

    /** An arrow pointing left: back to the screen before. */
    val Back: ImageVector by lazy {
        icon("Back") {
            outline {
                moveTo(19f, 12f)
                horizontalLineTo(5f)
                moveTo(11f, 6f)
                lineTo(5f, 12f)
                lineTo(11f, 18f)
            }
        }
    }

    /** A box with an arrow out of it: share a room's link. */
    val Share: ImageVector by lazy {
        icon("Share") {
            outline {
                moveTo(12f, 15f)
                verticalLineTo(3.5f)
                moveTo(7.5f, 8f)
                lineTo(12f, 3.5f)
                lineTo(16.5f, 8f)
                moveTo(8f, 11f)
                horizontalLineTo(5.5f)
                verticalLineTo(20.5f)
                horizontalLineTo(18.5f)
                verticalLineTo(11f)
                horizontalLineTo(16f)
            }
        }
    }

    /** A cross of two strokes: make a room. */
    val Plus: ImageVector by lazy {
        icon("Plus") {
            outline(width = BOLD) {
                moveTo(12f, 5f)
                verticalLineTo(19f)
                moveTo(5f, 12f)
                horizontalLineTo(19f)
            }
        }
    }

    /** A phone's keypad, three rows of three and one under: join a room by its code. */
    val Keypad: ImageVector by lazy {
        icon("Keypad") {
            path(fill = SolidColor(Color.Black)) {
                for (row in 0 until KEYPAD_ROWS) {
                    for (column in 0 until KEYPAD_COLUMNS) {
                        key(x = KEYPAD_LEFT + column * KEYPAD_STEP, y = KEYPAD_TOP + row * KEYPAD_STEP)
                    }
                }
                key(x = KEYPAD_LEFT + KEYPAD_STEP, y = KEYPAD_TOP + KEYPAD_ROWS * KEYPAD_STEP)
            }
        }
    }

    /** A globe: the rooms open to anyone. */
    val Globe: ImageVector by lazy {
        icon("Globe") {
            outline {
                circle(x = 12f, y = 12f, radius = 9f)
                moveTo(12f, 3f)
                arcTo(4f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 21f)
                arcTo(4f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 3f)
                moveTo(3f, 12f)
                horizontalLineTo(21f)
            }
        }
    }

    /** One player, alone: a game played solo. */
    val Person: ImageVector by lazy {
        icon("Person") {
            outline {
                circle(x = 12f, y = 8.5f, radius = 3.5f)
                moveTo(5f, 20f)
                curveTo(5f, 16.5f, 8f, 14f, 12f, 14f)
                curveTo(16f, 14f, 19f, 16.5f, 19f, 20f)
            }
        }
    }

    /** Three sliders: a room's settings. */
    val Sliders: ImageVector by lazy {
        icon("Sliders") {
            outline {
                moveTo(4f, 7f)
                horizontalLineTo(20f)
                moveTo(4f, 12f)
                horizontalLineTo(20f)
                moveTo(4f, 17f)
                horizontalLineTo(20f)
            }
            path(fill = SolidColor(Color.Black)) {
                circle(x = 9f, y = 7f, radius = 2.4f)
                circle(x = 15.5f, y = 12f, radius = 2.4f)
                circle(x = 7.5f, y = 17f, radius = 2.4f)
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight") {
            outline {
                moveTo(9.5f, 6.5f)
                lineTo(15f, 12f)
                lineTo(9.5f, 17.5f)
            }
        }
    }

    /** A bolt of lightning: first to answer right, second, third. */
    val Bolt: ImageVector by lazy {
        icon("Bolt") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(13.5f, 2f)
                lineTo(5f, 13.5f)
                horizontalLineTo(11f)
                lineTo(10f, 22f)
                lineTo(19f, 10f)
                horizontalLineTo(13f)
                close()
            }
        }
    }

    /** A crown of three points: the game's winner. */
    val Crown: ImageVector by lazy {
        icon("Crown") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(3f, 8f)
                lineTo(7.5f, 12f)
                lineTo(12f, 5f)
                lineTo(16.5f, 12f)
                lineTo(21f, 8f)
                lineTo(19f, 18f)
                horizontalLineTo(5f)
                close()
                moveTo(5f, 19.5f)
                horizontalLineTo(19f)
                verticalLineTo(21f)
                horizontalLineTo(5f)
                close()
            }
        }
    }

    /** A flag on its pole: report a question. */
    val Flag: ImageVector by lazy {
        icon("Flag") {
            outline {
                moveTo(6f, 21f)
                verticalLineTo(4f)
                moveTo(6f, 4.5f)
                curveTo(9f, 3f, 11f, 6f, 14f, 5f)
                curveTo(16f, 4.3f, 17.5f, 4f, 19f, 4.5f)
                verticalLineTo(13f)
                curveTo(17.5f, 12.5f, 16f, 12.8f, 14f, 13.5f)
                curveTo(11f, 14.5f, 9f, 11.5f, 6f, 13f)
            }
        }
    }

    /** A door with an arrow leaving through it: leave the room. */
    val Leave: ImageVector by lazy {
        icon("Leave") {
            outline {
                moveTo(10f, 4f)
                horizontalLineTo(5f)
                verticalLineTo(20f)
                horizontalLineTo(10f)
                moveTo(10.5f, 12f)
                horizontalLineTo(20f)
                moveTo(16f, 8f)
                lineTo(20f, 12f)
                lineTo(16f, 16f)
            }
        }
    }

    /** A clock's face: a question's time. */
    val Clock: ImageVector by lazy {
        icon("Clock") {
            outline {
                circle(x = 12f, y = 12f, radius = 8.5f)
                moveTo(12f, 7f)
                verticalLineTo(12f)
                lineTo(15.5f, 14f)
            }
        }
    }

    /** A laughing face: a reaction. */
    val Laugh: ImageVector by lazy {
        icon("Laugh") {
            outline {
                circle(x = 12f, y = 12f, radius = 9f)
                // Eyes shut with laughing, two arcs, and a mouth wide open.
                moveTo(7.5f, 10f)
                quadTo(9f, 8f, 10.5f, 10f)
                moveTo(13.5f, 10f)
                quadTo(15f, 8f, 16.5f, 10f)
            }
            path(fill = SolidColor(Color.Black)) {
                moveTo(7f, 13f)
                horizontalLineTo(17f)
                curveTo(17f, 16f, 14.8f, 18f, 12f, 18f)
                curveTo(9.2f, 18f, 7f, 16f, 7f, 13f)
                close()
            }
        }
    }

    /** A face with its mouth fallen open: a reaction. */
    val Wow: ImageVector by lazy {
        icon("Wow") {
            outline {
                circle(x = 12f, y = 12f, radius = 9f)
                circle(x = 12f, y = 15.5f, radius = 2.2f)
                moveTo(7.5f, 7.5f)
                lineTo(9.8f, 8.3f)
                moveTo(16.5f, 7.5f)
                lineTo(14.2f, 8.3f)
            }
            path(fill = SolidColor(Color.Black)) {
                circle(x = 9f, y = 10.5f, radius = 1.3f)
                circle(x = 15f, y = 10.5f, radius = 1.3f)
            }
        }
    }

    /** A heart: a reaction. */
    val Heart: ImageVector by lazy {
        icon("Heart") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 20.5f)
                curveTo(6f, 16.5f, 2.5f, 13f, 2.5f, 8.8f)
                curveTo(2.5f, 6f, 4.6f, 3.8f, 7.3f, 3.8f)
                curveTo(9.2f, 3.8f, 10.9f, 4.9f, 12f, 6.6f)
                curveTo(13.1f, 4.9f, 14.8f, 3.8f, 16.7f, 3.8f)
                curveTo(19.4f, 3.8f, 21.5f, 6f, 21.5f, 8.8f)
                curveTo(21.5f, 13f, 18f, 16.5f, 12f, 20.5f)
                close()
            }
        }
    }

    /** A flame: a reaction, for an answer that was hot. */
    val Flame: ImageVector by lazy {
        icon("Flame") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(12.6f, 6f, 18.5f, 8.5f, 18.5f, 14.5f)
                curveTo(18.5f, 18.3f, 15.6f, 21.5f, 12f, 21.5f)
                curveTo(8.4f, 21.5f, 5.5f, 18.3f, 5.5f, 14.5f)
                curveTo(5.5f, 11.5f, 7.2f, 9.6f, 8.6f, 8.2f)
                curveTo(8.7f, 10f, 9.4f, 11.2f, 10.4f, 11.8f)
                curveTo(10.2f, 8f, 10.8f, 4.6f, 12f, 2f)
                close()
            }
        }
    }

    /** A thumb up: a reaction. */
    val ThumbUp: ImageVector by lazy {
        icon("ThumbUp") {
            path(fill = SolidColor(Color.Black)) {
                moveTo(2.5f, 10f)
                horizontalLineTo(6.5f)
                verticalLineTo(20.5f)
                horizontalLineTo(2.5f)
                close()
                moveTo(8f, 10f)
                lineTo(11.2f, 3.5f)
                curveTo(11.6f, 2.7f, 12.5f, 2.3f, 13.4f, 2.5f)
                curveTo(14.5f, 2.8f, 15.1f, 3.9f, 14.8f, 5f)
                lineTo(13.9f, 8.5f)
                lineTo(19.5f, 8.5f)
                curveTo(20.8f, 8.5f, 21.8f, 9.7f, 21.6f, 11f)
                lineTo(20.6f, 18.6f)
                curveTo(20.4f, 19.7f, 19.5f, 20.5f, 18.4f, 20.5f)
                lineTo(8f, 20.5f)
                close()
            }
        }
    }

    /** The reactions a room may send, in the order the reactions row shows them. */
    val REACTIONS: List<ImageVector> by lazy { listOf(Laugh, Wow, Heart, Flame, ThumbUp) }

    private fun icon(
        name: String,
        draw: ImageVector.Builder.() -> Unit,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = SIZE.dp,
                defaultHeight = SIZE.dp,
                viewportWidth = SIZE,
                viewportHeight = SIZE,
            ).apply(draw)
            .build()

    /** One stroke of the icons' width, with round ends and corners. */
    private fun ImageVector.Builder.outline(
        width: Float = STROKE,
        draw: PathBuilder.() -> Unit,
    ) {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = draw,
        )
    }

    /** A circle of [radius] about ([x], [y]), in two half turns. */
    private fun PathBuilder.circle(
        x: Float,
        y: Float,
        radius: Float,
    ) {
        moveTo(x - radius, y)
        arcTo(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = x + radius, y1 = y)
        arcTo(radius, radius, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = x - radius, y1 = y)
        close()
    }

    /** A keypad's key, a rounded square about ([x], [y]). */
    private fun PathBuilder.key(
        x: Float,
        y: Float,
    ) {
        val half = KEY_SIZE / 2
        moveTo(x - half + KEY_ROUND, y - half)
        horizontalLineTo(x + half - KEY_ROUND)
        quadTo(x + half, y - half, x + half, y - half + KEY_ROUND)
        verticalLineTo(y + half - KEY_ROUND)
        quadTo(x + half, y + half, x + half - KEY_ROUND, y + half)
        horizontalLineTo(x - half + KEY_ROUND)
        quadTo(x - half, y + half, x - half, y + half - KEY_ROUND)
        verticalLineTo(y - half + KEY_ROUND)
        quadTo(x - half, y - half, x - half + KEY_ROUND, y - half)
        close()
    }

    /** The grid every icon is drawn on, which is also its size in dp. */
    private const val SIZE = 24f
    private const val STROKE = 2.2f
    private const val BOLD = 3f

    private const val KEYPAD_ROWS = 3
    private const val KEYPAD_COLUMNS = 3
    private const val KEYPAD_LEFT = 6.5f
    private const val KEYPAD_TOP = 4.5f
    private const val KEYPAD_STEP = 5.5f
    private const val KEY_SIZE = 3.6f
    private const val KEY_ROUND = 0.9f
}
