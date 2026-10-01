package io.ntole.kvizic.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.StageDialog
import io.ntole.kvizic.design.component.Toggle
import io.ntole.kvizic.language.LocalStrings

/**
 * The Statistics switch: whether the player lets the game send analytics, [on], which a tap anywhere on it
 * turns the other way, [onChange]. Its word and the switch are one control, which a screen reader hears as
 * the word, a switch, and on or off; a button under it opens a dialog saying what is sent and what never
 * is.
 */
@Composable
fun StatisticsSwitch(
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val strings = LocalStrings.current.aboutScreen
    val toggle = tapped("about.statistics") { onChange(!on) }
    var explaining by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Toggle(
            checked = on,
            onCheckedChange = { toggle() },
            label = strings.statistics,
            modifier = Modifier.fillMaxWidth(),
        )
        StageButton(
            strings.aboutStatistics,
            onClick = tapped("about.statistics_info") { explaining = true },
            kind = ButtonKind.QUIET,
            size = ButtonSize.SMALL,
        )
    }
    if (explaining) {
        StageDialog(onDismiss = { explaining = false }, title = strings.statistics, text = strings.statisticsInfo) {
            StageButton(
                strings.ok,
                onClick = tapped("about.statistics_info_ok") { explaining = false },
                size = ButtonSize.SMALL,
            )
        }
    }
}
