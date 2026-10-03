package io.ntole.kvizic.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.Divider
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.Toggle
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.LocalCues
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.LocalStrings

/**
 * The app's Settings screen, not a room's: what the player sets, in one place: the Sound switch, [soundOn]
 * whether the game makes sound, which [onSoundChange] changes; the script the game is written in, [language],
 * one of those offered (`Language.OFFERED`), two chips with no heading, each named in itself, which
 * [onLanguageChange] changes; and a way
 * to the About screen, [onAbout], where what the game keeps of the player is. It scrolls, its content held to
 * a readable column on a wide window.
 */
@Composable
fun AppSettingsScreen(
    modifier: Modifier = Modifier,
    soundOn: Boolean = true,
    onSoundChange: (Boolean) -> Unit = {},
    language: Language = Language.DEFAULT,
    onLanguageChange: (Language) -> Unit = {},
    onAbout: () -> Unit = {},
) {
    val space = KvizicTheme.space
    val strings = LocalStrings.current
    val words = strings.settingsScreen
    val cues = LocalCues.current
    // The switch's own sound is the old setting's: turned on, it is heard once it is on.
    var wasOn by remember { mutableStateOf(soundOn) }
    LaunchedEffect(soundOn) {
        if (soundOn && !wasOn) cues.play(Cue.TOGGLE_ON)
        wasOn = soundOn
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(space.sm),
            modifier = Modifier.widthIn(max = space.contentWidth).fillMaxWidth().padding(space.screen),
        ) {
            val toggleSound = tapped("settings.sound") { onSoundChange(!soundOn) }
            Toggle(
                checked = soundOn,
                onCheckedChange = { toggleSound() },
                label = words.sound,
                modifier = Modifier.fillMaxWidth(),
            )

            Divider()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(space.xs),
                verticalArrangement = Arrangement.spacedBy(space.xs),
            ) {
                Language.OFFERED.forEach { offered ->
                    Chip(
                        offered.ownName,
                        selected = offered == language,
                        onClick =
                            tapped("settings.language", mapOf("language" to offered.tag)) {
                                onLanguageChange(offered)
                            },
                    )
                }
            }

            Divider()
            StageButton(
                strings.aboutScreen.title,
                onClick = tapped("settings.about", onClick = onAbout),
                kind = ButtonKind.SECONDARY,
                size = ButtonSize.SMALL,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
