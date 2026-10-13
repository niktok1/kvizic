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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.region.Country
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
import io.ntole.kvizic.region.PLAYABLE_COUNTRIES

/**
 * The app's Settings screen, not a room's: what the player sets, in one place, a part each between dividers:
 * - the Sound switch, [soundOn] whether the game makes sound, which [onSoundChange] changes;
 * - the language the game is shown in, [language], one chip for each of those [offered] (`Language.OFFERED`),
 *   each named in itself, which [onLanguageChange] changes; a screen reader hears Serbian's two with their
 *   script, as both are Serbian;
 * - the country the player plays from, [country], one chip for each of the six, named in the screen's language,
 *   which [onCountryChange] changes, and which decides the questions of a country the player gets; the chips
 *   have no heading, and a screen reader hears what they are for (`SettingsScreenStrings.countrySaid`);
 * - and a way to the About screen, [onAbout], where what the game keeps of the player is.
 *
 * Neither row has a heading: each chip says what it is. A row too long for the width goes on to the next line.
 * It scrolls, its content held to a readable column on a wide window.
 */
@Composable
fun AppSettingsScreen(
    modifier: Modifier = Modifier,
    soundOn: Boolean = true,
    onSoundChange: (Boolean) -> Unit = {},
    language: Language = Language.DEFAULT,
    onLanguageChange: (Language) -> Unit = {},
    country: Country = Country.RS,
    onCountryChange: (Country) -> Unit = {},
    onAbout: () -> Unit = {},
    offered: List<Language> = Language.OFFERED,
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
                offered.forEach { offered ->
                    val said = offered.saidWithScript
                    Chip(
                        offered.ownName,
                        modifier = if (said == null) Modifier else Modifier.semantics { contentDescription = said },
                        selected = offered == language,
                        onClick =
                            tapped("settings.language", mapOf("language" to offered.tag)) {
                                onLanguageChange(offered)
                            },
                    )
                }
            }

            Divider()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(space.xs),
                verticalArrangement = Arrangement.spacedBy(space.xs),
                modifier = Modifier.semantics { contentDescription = words.countrySaid },
            ) {
                PLAYABLE_COUNTRIES.forEach { playable ->
                    val name = words.countries.of(playable) ?: return@forEach
                    Chip(
                        name,
                        selected = playable == country,
                        onClick =
                            tapped("settings.country", mapOf(AnalyticsProperty.COUNTRY to playable.name)) {
                                onCountryChange(playable)
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

/**
 * What a screen reader says of a language's chip where its name alone would not tell it from another's: Serbian's
 * two, both Serbian, with their script, each in itself. None for the rest, which are said as their names.
 */
private val Language.saidWithScript: String?
    get() =
        when (this) {
            Language.SERBIAN_CYRILLIC -> "Српски, ћирилица"
            Language.SERBIAN_LATIN -> "Srpski, latinica"
            else -> null
        }
