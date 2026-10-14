package io.ntole.kvizic.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import io.ntole.kvizic.design.component.FlagChip
import io.ntole.kvizic.design.component.KvizicIcon
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.Toggle
import io.ntole.kvizic.design.flag.FlagArt
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.LocalCues
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.region.PLAYABLE_COUNTRIES
import io.ntole.kvizic.region.RegionLanguages

/**
 * The app's Settings screen, not a room's: what the player sets, in one place, a part each between dividers:
 * - the Sound switch, [soundOn] whether the game makes sound, which [onSoundChange] changes;
 * - the region (the owner, 2026-10-14): a globe, then the six countries' flags and no word, [country] the one
 *   picked; a flag tapped picks its country, [onCountryChange], which decides the questions of a country the
 *   player gets, and the language with it, [onLanguageChange], the one [RegionLanguages.picked] gives for the
 *   [language] the game is in now (a player in Latin stays in Latin where the country reads both). A screen
 *   reader hears each flag as its country, named in the screen's language, and what the row is for
 *   (`SettingsScreenStrings.countrySaid`);
 * - under it, only where [country] reads two scripts, Serbia's and Montenegro's Serbian and Bosnia's Bosnian
 *   (Latin) or Serbian (Cyrillic): Ћирилица and Latinica on two chips, each written in itself, [language] the
 *   one picked, among those [offered] (`Language.OFFERED`);
 * - and a way to the About screen, [onAbout], where what the game keeps of the player is.
 *
 * No row has a heading. A row too long for the width goes on to the next line. It scrolls, its content held to a
 * readable column on a wide window.
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(space.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = words.countrySaid },
            ) {
                KvizicIcon(KvizicIcons.Globe, contentDescription = null)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    PLAYABLE_COUNTRIES.forEach { playable ->
                        val name = words.countries.of(playable) ?: return@forEach
                        val flag = FlagArt.of(playable.name) ?: return@forEach
                        FlagChip(
                            flag = flag,
                            contentDescription = name,
                            selected = playable == country,
                            onClick =
                                tapped("settings.country", mapOf(AnalyticsProperty.COUNTRY to playable.name)) {
                                    onCountryChange(playable)
                                    onLanguageChange(RegionLanguages.picked(playable, language, offered))
                                },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            val scripts = RegionLanguages.choices(country, offered)
            if (scripts.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                    verticalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    // Cyrillic first, as the game's own script is.
                    scripts.sortedByDescending { it.isCyrillic }.forEach { script ->
                        Chip(
                            script.scriptName,
                            modifier = Modifier.semantics { contentDescription = script.saidWithScript },
                            selected = script == language,
                            onClick =
                                tapped("settings.language", mapOf("language" to script.tag)) {
                                    onLanguageChange(script)
                                },
                        )
                    }
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

/** A script's chip, written in that script whatever the screen is in. */
private val Language.scriptName: String
    get() = if (isCyrillic) "Ћирилица" else "Latinica"

/**
 * What a screen reader says of a script's chip: the language it shows the game in and its script, each in itself
 * („Bosanski, latinica“, „Српски, ћирилица“), since a script alone does not say whether Bosnia's is Bosnian.
 */
private val Language.saidWithScript: String
    get() = "$ownName, ${if (isCyrillic) "ћирилица" else "latinica"}"
