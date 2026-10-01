package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Where a question's answer tiles stand on the screen, kept above the screens that show them, so a grid of
 * the same question's tiles that comes after another takes the tiles over from it: as the answers give way
 * to their reveal, the reveal's tiles glide from where the answers' stood to their own places, and the
 * answers' own go the same frame, so a tile is never drawn twice. Plain fields, written and read in
 * placement and draw alone; a screen made anew, on a rotation, keeps none.
 */
@Stable
class TilePlaces internal constructor() {
    /** The question the tiles shown are of, and the grid that shows them. */
    internal var question: Any? = null
    internal var grid: GridOfTiles? = null

    /** Where each of the shown grid's tiles stands, by its answer's index, in the root's pixels. */
    internal val bounds = HashMap<Int, Rect>()
}

/** A [TilePlaces] that lives as long as the composition calling it. */
@Composable
fun rememberTilePlaces(): TilePlaces = remember { TilePlaces() }

/**
 * One grid of tiles, as [TilePlaces] tells grids apart: the places its tiles take over, from the grid of
 * the same question shown before it, if any, the first time one of its tiles is placed.
 */
internal class GridOfTiles(
    private val places: TilePlaces,
    private val question: Any,
) {
    private var claimed = false

    /** The grid's tiles, each to be drawn again, as nothing, once a later grid takes them over. */
    val tiles = mutableSetOf<DrawModifierNode>()

    /** Where the tiles of the grid this one took over stood, by index. */
    var from: Map<Int, Rect> = emptyMap()
        private set

    /** Takes the tiles over, once, as the first of them is placed. */
    fun claim() {
        if (claimed) return
        claimed = true
        val before = places.grid?.takeIf { places.question == question }
        from = if (before != null) HashMap(places.bounds) else emptyMap()
        places.question = question
        places.grid = this
        places.bounds.clear()
        // Its layer holds them as last drawn, which no fade of it draws again.
        before?.tiles?.forEach { it.invalidateDraw() }
    }

    /** Whether a grid of the same question took this one's tiles over, which are then drawn no more. */
    val overtaken: Boolean get() = claimed && places.grid !== this && places.question == question

    fun placed(
        index: Int,
        bounds: Rect,
    ) {
        if (places.grid === this) places.bounds[index] = bounds
    }
}

/**
 * The tile of [index] in [grid]: drawn from where the tile it takes over stood, gliding to its own place
 * over [millis], the gap closing and the size meeting its own; nothing once a later grid took it over.
 */
internal fun Modifier.glidingTile(
    grid: GridOfTiles,
    index: Int,
    millis: Int,
): Modifier = this then GlidingTileElement(grid, index, millis)

private data class GlidingTileElement(
    val grid: GridOfTiles,
    val index: Int,
    val millis: Int,
) : ModifierNodeElement<GlidingTileNode>() {
    override fun create(): GlidingTileNode = GlidingTileNode(grid, index, millis)

    override fun update(node: GlidingTileNode) {
        node.update(grid, index, millis)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glidingTile"
        properties["index"] = index
    }
}

private class GlidingTileNode(
    private var grid: GridOfTiles,
    private var index: Int,
    private var millis: Int,
) : Modifier.Node(),
    LayoutAwareModifierNode,
    DrawModifierNode {
    private var placed = false
    private var from: Rect? = null
    private var at: Rect = Rect.Zero
    private var glide = Animatable(1f)
    private var gliding: Job? = null

    override fun onAttach() {
        grid.tiles += this
    }

    override fun onDetach() {
        grid.tiles -= this
    }

    fun update(
        grid: GridOfTiles,
        index: Int,
        millis: Int,
    ) {
        if (grid !== this.grid && isAttached) {
            this.grid.tiles -= this
            grid.tiles += this
        }
        this.grid = grid
        this.index = index
        this.millis = millis
    }

    override fun onPlaced(coordinates: LayoutCoordinates) {
        at = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
        if (!placed) {
            placed = true
            grid.claim()
            val was = grid.from[index]
            if (was != null && was != at) {
                from = was
                // From this very frame, which a phone draws before the glide's coroutine runs.
                val next = Animatable(0f)
                glide = next
                gliding?.cancel()
                gliding =
                    coroutineScope.launch {
                        next.animateTo(1f, tween(millis.coerceAtLeast(1), easing = FastOutSlowInEasing))
                    }
                invalidateDraw()
            }
        }
        grid.placed(index, at)
    }

    override fun ContentDrawScope.draw() {
        if (grid.overtaken) return
        val from = from
        val t = glide.value
        if (from == null || t >= 1f || at.width <= 0f || at.height <= 0f) {
            drawContent()
            return
        }
        // Where the tile it took over stood, closing on its own place: its corner moved, its size scaled.
        val left = (from.left - at.left) * (1f - t)
        val top = (from.top - at.top) * (1f - t)
        val scaleX = 1f + (from.width / at.width - 1f) * (1f - t)
        val scaleY = 1f + (from.height / at.height - 1f) * (1f - t)
        translate(left, top) {
            scale(scaleX, scaleY, pivot = Offset.Zero) { this@draw.drawContent() }
        }
    }
}
