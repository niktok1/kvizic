package io.ntole.kvizic.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

/**
 * The desktop's share: no share sheet, so what is shared goes on the clipboard, an image and its text as
 * one, to paste into whatever takes them, an image where it takes one and the text where only text goes.
 */
@Composable
internal actual fun rememberShareSheet(): ShareSheet = remember { DesktopShareSheet }

private object DesktopShareSheet : ShareSheet {
    override suspend fun share(
        image: ImageBitmap,
        text: String,
    ): ShareOutcome = copy(ImageAndText(image.toAwtImage(), text))

    override suspend fun shareText(text: String): ShareOutcome = copy(StringSelection(text))

    private fun copy(contents: Transferable): ShareOutcome =
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(contents, null)
            ShareOutcome.COPIED
        } catch (_: Exception) {
            // No clipboard (a headless machine), or another app holds it.
            ShareOutcome.FAILED
        }
}

/** An image and a text on the clipboard together. */
private class ImageAndText(
    private val image: Image,
    private val text: String,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.imageFlavor, DataFlavor.stringFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in transferDataFlavors

    override fun getTransferData(flavor: DataFlavor): Any =
        when (flavor) {
            DataFlavor.imageFlavor -> image
            DataFlavor.stringFlavor -> text
            else -> throw UnsupportedFlavorException(flavor)
        }
}
