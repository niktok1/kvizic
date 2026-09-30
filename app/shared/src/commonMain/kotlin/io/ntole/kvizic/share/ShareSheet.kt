package io.ntole.kvizic.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap

/**
 * The platform's way to share: Android's share sheet, iOS's share sheet, a browser's Web Share, or the
 * desktop's clipboard. It never throws: what it did, or that nothing could, is its [ShareOutcome].
 */
interface ShareSheet {
    /** Shares [image] with [text] beside it: a game's results with a line inviting others, say. */
    suspend fun share(
        image: ImageBitmap,
        text: String,
    ): ShareOutcome

    /** Shares [text] alone: a lobby's invitation link, say. */
    suspend fun shareText(text: String): ShareOutcome
}

/** What a [ShareSheet] did. */
enum class ShareOutcome {
    /** The platform's share sheet opened, which is the player's from then on. */
    OPENED,

    /** What was shared is on the clipboard: the desktop's, or a browser's that cannot share. */
    COPIED,

    /** The image was downloaded and the text copied: a browser that cannot share a file. */
    SAVED,

    /** Nothing could share it. */
    FAILED,
}

/** Shares nothing: what a screen drawn alone, with no [LocalShareSheet] provided, has. */
private object NoShareSheet : ShareSheet {
    override suspend fun share(
        image: ImageBitmap,
        text: String,
    ): ShareOutcome = ShareOutcome.FAILED

    override suspend fun shareText(text: String): ShareOutcome = ShareOutcome.FAILED
}

/** The app's [ShareSheet], which `App` provides ([rememberShareSheet]). */
val LocalShareSheet = staticCompositionLocalOf<ShareSheet> { NoShareSheet }

/** This platform's [ShareSheet]. */
@Composable
internal expect fun rememberShareSheet(): ShareSheet
