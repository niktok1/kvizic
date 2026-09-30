package io.ntole.kvizic.about

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.failureText

/**
 * Deleting the account, at the bottom of the About screen: a quiet button, which asks in a dialog of one
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
    val colors = MaterialTheme.colorScheme
    var confirming by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (deletion is Deletion.Failed) {
            Text(
                text = strings.failureText(deletion.error, deletion.retryAfter),
                color = colors.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(
            onClick = tapped("about.delete_account") { confirming = true },
            enabled = deletion != Deletion.InFlight,
            colors = ButtonDefaults.textButtonColors(contentColor = colors.onSurfaceVariant),
        ) {
            Text(words.button)
        }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            text = { Text(words.warning) },
            confirmButton = {
                TextButton(
                    onClick =
                        tapped("about.delete_account_confirm") {
                            confirming = false
                            onDelete()
                        },
                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
                ) {
                    Text(words.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = tapped("about.delete_account_cancel") { confirming = false }) {
                    Text(strings.cancel)
                }
            },
        )
    }
}
