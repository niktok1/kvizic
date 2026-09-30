package io.ntole.kvizic.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.ntole.kvizic.analytics.tapped
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = on, role = Role.Switch, onValueChange = { toggle() })
                    .minimumInteractiveComponentSize(),
        ) {
            Text(text = strings.statistics, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Spacer(Modifier.weight(1f))
            // No click of its own: the row's toggleable is the one.
            Switch(checked = on, onCheckedChange = null)
        }
        TextButton(onClick = tapped("about.statistics_info") { explaining = true }) {
            Text(strings.aboutStatistics)
        }
    }
    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            text = { Text(strings.statisticsInfo) },
            confirmButton = {
                TextButton(onClick = tapped("about.statistics_info_ok") { explaining = false }) {
                    Text(strings.ok)
                }
            },
        )
    }
}
