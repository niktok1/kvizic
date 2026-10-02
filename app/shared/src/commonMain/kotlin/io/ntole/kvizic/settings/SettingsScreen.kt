package io.ntole.kvizic.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.ntole.kvizic.about.AccountId
import io.ntole.kvizic.about.StatisticsSwitch
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Divider
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.Toggle
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.language.LocalStrings

/**
 * The Settings screen: what the player sets, in one place. The Sound switch, [soundOn] whether the game
 * makes sound, which [onSoundChange] changes; the Statistics switch, [statisticsOn] whether the player lets
 * the game send analytics, which [onStatisticsChange] changes; a way to the About screen, [onAbout]; and
 * last the player's account: its id, [accountId], to copy and send when asking by email for the account to
 * be deleted, none while no session is stored, and [deletion], the quiet way to delete it. It scrolls, its
 * content held to a readable column on a wide window.
 */
@Composable
fun SettingsScreen(
    accountId: String?,
    modifier: Modifier = Modifier,
    soundOn: Boolean = true,
    onSoundChange: (Boolean) -> Unit = {},
    statisticsOn: Boolean = true,
    onStatisticsChange: (Boolean) -> Unit = {},
    onAbout: () -> Unit = {},
    deletion: @Composable () -> Unit = {},
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val strings = LocalStrings.current
    val words = strings.settingsScreen

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
            StatisticsSwitch(on = statisticsOn, onChange = onStatisticsChange)

            Divider()
            StageButton(
                strings.aboutScreen.title,
                onClick = tapped("settings.about", onClick = onAbout),
                kind = ButtonKind.SECONDARY,
                size = ButtonSize.SMALL,
                modifier = Modifier.fillMaxWidth(),
            )

            Divider()
            KvizicText(text = words.account, style = type.bodyStrong)
            accountId?.let { id -> AccountId(id) }
            deletion()
        }
    }
}
