package io.ntole.kvizic.about

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.StageDialog
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.failureText

/**
 * Deleting the account, at the bottom of the Settings screen: a quiet button, which asks in a dialog of one
 * line whether everything is to go for good, and only then deletes, [onDelete]; why the last one failed,
 * above it. Off while a deletion is in flight.
 */
@Composable
fun DeleteAccountButton(
    deletion: Deletion,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val words = strings.aboutScreen.deleteAccount
    var confirming by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (deletion is Deletion.Failed) {
            KvizicText(
                text = strings.failureText(deletion.error, deletion.retryAfter),
                color = KvizicTheme.colors.loss,
                style = KvizicTheme.type.caption,
            )
        }
        StageButton(
            words.button,
            onClick = tapped("about.delete_account") { confirming = true },
            kind = ButtonKind.QUIET,
            size = ButtonSize.SMALL,
            enabled = deletion != Deletion.InFlight,
        )
    }
    if (confirming) {
        StageDialog(onDismiss = { confirming = false }, title = words.button, text = words.warning) {
            StageButton(
                strings.cancel,
                onClick = tapped("about.delete_account_cancel") { confirming = false },
                kind = ButtonKind.QUIET,
                size = ButtonSize.SMALL,
            )
            StageButton(
                words.confirm,
                onClick =
                    tapped("about.delete_account_confirm") {
                        confirming = false
                        onDelete()
                    },
                kind = ButtonKind.DARK,
                size = ButtonSize.SMALL,
            )
        }
    }
}
