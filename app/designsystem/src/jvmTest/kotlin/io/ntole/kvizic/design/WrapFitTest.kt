package io.ntole.kvizic.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.StageDialog
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Buttons' words whole at a phone's large font (130%, a common setting) and on a narrow phone, in every skin:
 * a tile's words wrap rather than lose a word, a label too long for its line wraps where it may, and a dialog
 * whose buttons do not fit its row stacks them, the one that goes ahead on top, rather than squeeze it.
 */
class WrapFitTest {
    @Test
    fun `two tiles side by side keep their words whole at a large font, on one line at the usual one`() {
        Skins.ALL.forEach { skin ->
            listOf(1f, LARGE_FONT).forEach { fontScale ->
                val scene = stageScene(skin, PHONE_WIDTH, PHONE_HEIGHT, fontScale = fontScale) { Tiles() }
                try {
                    writeDesign("wrap-tiles-${skin.id}-$fontScale", scene.renderUpTo(SETTLED))
                    val where = "${skin.id} at $fontScale"
                    assertEquals(emptyList(), scene.cutTexts(), "$where: cut")
                    if (fontScale == 1f) {
                        TILES.forEach { assertEquals(1, layoutOf(scene, it).lineCount, "$where: \"$it\" wraps") }
                    }
                    TILES.forEach { label ->
                        val laid = layoutOf(scene, label)
                        val text = laid.layoutInput.text.text
                        (0 until laid.lineCount - 1).forEach { line ->
                            val end = laid.getLineEnd(line)
                            assertTrue(text[end - 1] == ' ' || text[end] == ' ', "$where: \"$label\" broken in a word")
                        }
                    }
                } finally {
                    scene.close()
                }
            }
        }
    }

    @Test
    fun `a dialog stacks the buttons its row cannot hold, the one that goes ahead on top`() {
        Skins.ALL.forEach { skin ->
            DIALOG_WIDTHS.forEach { width ->
                listOf(1f, LARGE_FONT).forEach { fontScale ->
                    val scene = stageScene(skin, width, PHONE_HEIGHT, fontScale = fontScale) { Dialog(THREE) }
                    try {
                        writeDesign("wrap-dialog-${skin.id}-$width-$fontScale", scene.renderUpTo(SETTLED))
                        val where = "${skin.id} at $width, $fontScale"
                        assertEquals(emptyList(), scene.cutTexts(), "$where: cut")
                        val (cancel, _, kick) = THREE.map { label -> boundsOf(scene, label) }
                        val row = cancel.top == kick.top
                        assertTrue(
                            if (row) kick.left > cancel.right else kick.bottom <= cancel.top,
                            "$where: the main action is not last in the row nor on top: $cancel, $kick",
                        )
                    } finally {
                        scene.close()
                    }
                }
            }
            val scene = stageScene(skin, PHONE_WIDTH, PHONE_HEIGHT) { Dialog(TWO) }
            try {
                val (no, yes) = TWO.map { label -> boundsOf(scene.also { it.renderUpTo(SETTLED) }, label) }
                assertEquals(no.center.y, yes.center.y, "${skin.id}: two short buttons are not in a row")
                assertTrue(yes.left > no.right, "${skin.id}: the one that goes ahead is not last")
            } finally {
                scene.close()
            }
        }
    }

    @Test
    fun `a button's words wrap onto the lines it allows rather than being cut`() {
        Skins.ALL.forEach { skin ->
            val scene =
                stageScene(skin, PHONE_WIDTH, PHONE_HEIGHT, fontScale = LARGE_FONT) {
                    Column(Modifier.padding(KvizicTheme.space.screen)) {
                        StageButton(
                            LONG_REASON,
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            kind = ButtonKind.SECONDARY,
                            size = ButtonSize.SMALL,
                            maxLines = REASON_LINES,
                        )
                    }
                }
            try {
                scene.renderUpTo(SETTLED)
                assertEquals(emptyList(), scene.cutTexts(), "${skin.id}: cut")
            } finally {
                scene.close()
            }
        }
    }

    @Composable
    private fun Tiles() {
        val space = KvizicTheme.space
        Row(Modifier.padding(space.screen), horizontalArrangement = Arrangement.spacedBy(space.md)) {
            listOf(KvizicIcons.Plus to TILES[0], KvizicIcons.Keypad to TILES[1]).forEach { (icon, label) ->
                StageButton(
                    label,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                    kind = ButtonKind.SECONDARY,
                    icon = icon,
                    iconAbove = true,
                )
            }
        }
    }

    @Composable
    private fun Dialog(labels: List<String>) {
        StageDialog(onDismiss = {}, title = "Нина") {
            labels.forEachIndexed { i, label ->
                val last = i == labels.lastIndex
                StageButton(
                    label,
                    onClick = {},
                    kind = if (last) ButtonKind.DARK else ButtonKind.QUIET,
                    size = ButtonSize.SMALL,
                    icon = KvizicIcons.Boot.takeIf { last && labels.size > 2 },
                )
            }
        }
    }

    private fun boundsOf(
        scene: androidx.compose.ui.ImageComposeScene,
        label: String,
    ) = scene
        .nodes()
        .single { node -> node.texts.any { it.equals(label, ignoreCase = true) } }
        .boundsInRoot

    private fun layoutOf(
        scene: androidx.compose.ui.ImageComposeScene,
        label: String,
    ): TextLayoutResult {
        val node = scene.everyNode().single { node -> node.texts.any { it.equals(label, ignoreCase = true) } }
        val results = mutableListOf<TextLayoutResult>()
        node.config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(results)
        return results.single()
    }

    private companion object {
        const val SETTLED = 1_000L * MILLI

        /** A phone's large font, 130%, a common setting. */
        const val LARGE_FONT = 1.3f
        const val PHONE_WIDTH = 360
        const val PHONE_HEIGHT = 640

        /** A phone a little wider than the narrowest, and a small tablet, in dp at one pixel each. */
        val DIALOG_WIDTHS = listOf(393, 800)
        val TILES = listOf("Направи собу", "Уђи кодом")
        val THREE = listOf("Откажи", "Нека води", "Избаци")
        val TWO = listOf("Не", "Да")
        const val REASON_LINES = 3
        const val LONG_REASON = "Нејасно је, или је више одговора тачно"
    }
}
