package io.ntole.kvizic.sound

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.LocalCues
import kotlin.time.TimeSource

/**
 * Gives [content] the game's sound ([LocalCues]): the platform's device, a [SoundEngine] over it, off when
 * the player turned [sounds] off or the app is not shown, and playing from the bank of the skin worn.
 * Draws inside the theme: wearing another skin loads that skin's bank and the next cue plays from it,
 * with nothing in [content] recomposed, since the engine is one object that follows the skin.
 */
@Composable
internal fun ProvideCues(
    sounds: SoundViewModel,
    device: SoundDevice = rememberSoundDevice(),
    content: @Composable () -> Unit,
) {
    val engine =
        remember(device) {
            val started = TimeSource.Monotonic.markNow()
            SoundEngine(device, nowMillis = { started.elapsedNow().inWholeMilliseconds })
        }
    val skin = KvizicTheme.skin.sound
    // Read here, in the composition, so the switch recomposes this: read inside the SideEffect it would be read
    // after the composition, and a switch turned would change nothing until something else recomposed it.
    val enabled = sounds.enabled.collectAsStateWithLifecycle().value
    SideEffect {
        engine.enabled = enabled
        engine.skin = skin
    }
    LifecycleStartEffect(engine) {
        engine.foreground = true
        onStopOrDispose { engine.foreground = false }
    }
    LaunchedEffect(engine, skin.bank) { engine.load(skin) }
    DisposableEffect(device) { onDispose { device.release() } }
    CompositionLocalProvider(LocalCues provides engine, content = content)
}
