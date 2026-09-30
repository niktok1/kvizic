package io.ntole.kvizic.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import js.array.jsArrayOf
import js.objects.unsafeJso
import js.typedarrays.toUint8Array
import kotlinx.coroutines.CancellationException
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import web.clipboard.writeText
import web.dom.document
import web.file.File
import web.file.FilePropertyBag
import web.html.HtmlTagName
import web.navigator.navigator
import web.navigator.share
import web.share.ShareData
import web.url.URL

/**
 * A browser's share: Web Share, with an image as a file where the browser shares files (most phones'
 * browsers); elsewhere an image downloaded and its text copied, or a text alone copied.
 */
@Composable
internal actual fun rememberShareSheet(): ShareSheet = remember { WebShareSheet }

private object WebShareSheet : ShareSheet {
    override suspend fun share(
        image: ImageBitmap,
        text: String,
    ): ShareOutcome {
        val bytes = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes
        if (bytes == null) return ShareOutcome.FAILED
        val file = File(jsArrayOf(bytes.toUint8Array()), FILE_NAME, unsafeJso<FilePropertyBag> { type = PNG })
        val data =
            unsafeJso<ShareData> {
                files = jsArrayOf(file)
                this.text = text
            }
        return sharing(data) {
            download(file)
            copy(text)
            ShareOutcome.SAVED
        }
    }

    override suspend fun shareText(text: String): ShareOutcome =
        sharing(unsafeJso<ShareData> { this.text = text }) {
            copy(text)
            ShareOutcome.COPIED
        }

    /** Web Share with [data] where the browser takes it, else [otherwise]; a failure of either is FAILED. */
    private suspend fun sharing(
        data: ShareData,
        otherwise: suspend () -> ShareOutcome,
    ): ShareOutcome =
        try {
            if (navigator.canShare(data)) {
                try {
                    navigator.share(data)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // The player closed the sheet, which is theirs to close: nothing failed.
                }
                ShareOutcome.OPENED
            } else {
                otherwise()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ShareOutcome.FAILED
        }
}

/** [file] downloaded, through a link made for it and clicked. */
private fun download(file: File) {
    val url = URL.createObjectURL(file)
    val link = document.createElement(HtmlTagName.a)
    link.href = url
    link.download = FILE_NAME
    link.click()
    URL.revokeObjectURL(url)
}

/** [text] on the clipboard, best effort: a page not focused may not write it. */
private suspend fun copy(text: String) {
    try {
        navigator.clipboard.writeText(text)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        // A download is the share where there is one; alone, a text not copied is said by nothing.
    }
}

private const val FILE_NAME = "kvizic.png"
private const val PNG = "image/png"
