package io.ntole.kvizic.design

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.skin.KvizicSkin
import io.ntole.kvizic.design.skin.Skin
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.skia.Image
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext

// Drawing the design system off screen, as a phone would, where there is no Compose UI test library.

/**
 * A scene of [width] by [height] pixels at [density], its type set [fontScale] times as large as a phone set
 * so does, wearing [skin], [content] on its stage.
 */
internal fun stageScene(
    skin: Skin,
    width: Int,
    height: Int,
    density: Float = 1f,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
): ImageComposeScene =
    ImageComposeScene(width = width, height = height, density = Density(density, fontScale)) {
        KvizicSkin(skin) { Stage(Modifier.fillMaxSize()) { content() } }
    }

/**
 * Draws the scene at [nanoTime] until what that frame changed shows. Drawn once, a frame can miss what
 * the desktop's snapshot manager does meanwhile on a thread of its own; the same frame drawn again
 * changes nothing else.
 */
internal fun ImageComposeScene.renderAt(nanoTime: Long) {
    repeat(3) {
        Snapshot.sendApplyNotifications()
        render(nanoTime).close()
    }
}

/**
 * The scene drawn up to [nanoTime], and that frame as an image: a frame at a time, at 60 a second, through
 * the first half second, where the steps give way to each other, then a frame each 100 ms, drawn once. What
 * runs on the frame clock takes its value from the frame's time, so the last frame is the one 60 a second
 * would draw; a slow frame that misses the snapshot manager only starts an animation a frame late.
 */
internal fun ImageComposeScene.renderUpTo(nanoTime: Long): Image {
    var t = 0L
    while (t < minOf(nanoTime, SMOOTH)) {
        renderAt(t)
        t += FRAME
    }
    while (t < nanoTime) {
        Snapshot.sendApplyNotifications()
        render(t).close()
        t += SLOW_FRAME
    }
    renderAt(nanoTime)
    return render(nanoTime)
}

/** Every node the scene holds, each on its own, from the top down. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun ImageComposeScene.everyNode(): List<SemanticsNode> =
    semanticsOwners
        .flatMap { owner -> owner.getAllSemanticsNodes(mergingEnabled = false) }
        .sortedWith(compareBy({ it.positionInRoot.y }, { it.positionInRoot.x }))

/** Every node the scene holds, a button's text and name merged into it, from the top down. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun ImageComposeScene.nodes(): List<SemanticsNode> =
    semanticsOwners
        .flatMap { owner -> owner.getAllSemanticsNodes(mergingEnabled = true) }
        .sortedWith(compareBy({ it.positionInRoot.y }, { it.positionInRoot.x }))

/**
 * Every text the scene lays out that does not fit where it stands, cut or ended in an ellipsis, each as
 * written: what a screen reader would read whole and the screen shows only in part.
 */
internal fun ImageComposeScene.cutTexts(): List<String> =
    everyNode().flatMap { node ->
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return@flatMap emptyList()
        val results = mutableListOf<TextLayoutResult>()
        action(results)
        if (results.any { it.isCut }) node.texts else emptyList()
    }

/**
 * Whether the text is not all shown: lines past its most, its lines more than a pixel taller than its box (a
 * pixel of a line's leading, rounded, cuts no letter), a line ended in an ellipsis, or a line wider than the
 * text's box. Not [TextLayoutResult.hasVisualOverflow] alone, which takes a short text in a wide paragraph, laid
 * out the width of what holds it and shown at its own, for one cut.
 */
internal val TextLayoutResult.isCut: Boolean
    get() =
        multiParagraph.didExceedMaxLines ||
            multiParagraph.height > size.height + ONE_PIXEL ||
            (0 until lineCount).any { line ->
                isLineEllipsized(line) || getLineRight(line) - getLineLeft(line) > size.width + HALF_PIXEL
            }

private const val HALF_PIXEL = 0.5f
private const val ONE_PIXEL = 1f

internal val SemanticsNode.texts: List<String>
    get() = config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

internal val SemanticsNode.descriptions: List<String>
    get() = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()

/** Every pixel of [image], as ARGB, row by row. */
internal fun pixelsOf(image: Image): IntArray {
    val map = image.toComposeImageBitmap().toPixelMap()
    return IntArray(map.width * map.height) { i ->
        val c = map[i % map.width, i / map.width]
        (c.alpha * 255).toInt() shl 24 or ((c.red * 255).toInt() shl 16) or ((c.green * 255).toInt() shl 8) or
            (c.blue * 255).toInt()
    }
}

/** Where the PNGs for a person to look at go: the directory `KVIZIC_DESIGN_DIR` names, or none. */
internal val designDirectory: File? =
    System
        .getenv("KVIZIC_DESIGN_DIR")
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.also { it.mkdirs() }

/** Writes [image] as [name].png into [designDirectory], when there is one. */
internal fun writeDesign(
    name: String,
    image: Image,
) {
    val directory = designDirectory ?: return
    File(directory, "$name.png").writeBytes(checkNotNull(image.encodeToData()) { "$name encodes to nothing" }.bytes)
}

internal const val FRAME: Long = 1_000_000_000L / 60
internal const val MILLI: Long = 1_000_000L

/** How long [renderUpTo] draws at 60 frames a second. */
private const val SMOOTH = 500L * MILLI

/** [renderUpTo]'s frame after [SMOOTH]. */
private const val SLOW_FRAME = 100L * MILLI

/**
 * Runs what is dispatched to it only once [drain]ed: a scene's effects, which it then runs as a phone does,
 * after the frame that launched them is drawn, where the scenes' own runs each at once, before it.
 */
internal class AfterTheFrame : CoroutineDispatcher() {
    private val queue = ConcurrentLinkedQueue<Runnable>()

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        queue += block
    }

    fun drain() {
        while (true) (queue.poll() ?: return).run()
    }
}
