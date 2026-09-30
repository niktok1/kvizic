package io.ntole.kvizic.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * TEMPORARY. The shell's stand-in theme until `:app:designsystem`'s skins replace it: Material 3's own
 * dark colour scheme and type scale, and the few spaces the shell's screens need. It exists only so
 * those screens hold no literal of their own and the swap touches one place. Build nothing new on it.
 */

/** The shell's colours: Material 3's default dark scheme, always dark, as the game's stage will be. */
val ShellColorScheme: ColorScheme = darkColorScheme()

/** The spaces the shell's screens use, so none holds a dp literal. */
@Immutable
data class ShellDimens(
    val spaceXs: Dp = 4.dp,
    val spaceSm: Dp = 8.dp,
    val spaceMd: Dp = 16.dp,
    val spaceLg: Dp = 24.dp,
    val spaceXl: Dp = 32.dp,
    val screenPadding: Dp = 16.dp,
    /** The widest a column of content grows on a wide window, a desktop's or a tablet's. */
    val contentMaxWidth: Dp = 600.dp,
    /** A top bar's height, the one on About. */
    val topBarHeight: Dp = 48.dp,
)

val LocalShellDimens = staticCompositionLocalOf { ShellDimens() }

/** Shows [content] in the shell's theme. */
@Composable
fun ShellTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalShellDimens provides ShellDimens()) {
        MaterialTheme(colorScheme = ShellColorScheme, content = content)
    }
}

/** The shell theme's parts, as a screen reads them. */
object Shell {
    val dimens: ShellDimens
        @Composable get() = LocalShellDimens.current

    /** Text shown as the machine reads it, an account id: every character the same width. */
    val codeStyle: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
}
