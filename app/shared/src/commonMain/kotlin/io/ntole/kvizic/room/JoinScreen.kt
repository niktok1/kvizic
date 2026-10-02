package io.ntole.kvizic.room

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.component.PanelKind
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.LocalCues
import io.ntole.kvizic.home.Notice
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.loading.LoadingSpinner

/** How many digits a room's code has. */
internal const val CODE_LENGTH = 6

/**
 * Joining a room by its code: six cells the digits fill, a keypad of the skin's buttons, and Join once all
 * six are in ([onJoin]); a hardware keyboard types and deletes too, and Enter joins. [code] is what is typed,
 * which [onCode] changes; [entry] turns the keypad off while the seat is taken, and says why it could not be.
 * Once all six digits are in, [preview] shows the room they name, its topics named by [topics], so the player
 * joins knowing what they join.
 */
@Composable
fun JoinScreen(
    code: String,
    onCode: (String) -> Unit,
    onJoin: () -> Unit,
    entry: Entry,
    onDismissFailure: () -> Unit,
    modifier: Modifier = Modifier,
    preview: RoomPreview = RoomPreview.None,
    topics: List<Topic> = emptyList(),
) {
    val strings = LocalStrings.current
    val words = strings.game
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val taking = entry is Entry.Taking
    val focus = remember { FocusRequester() }
    // Once drawn, so a keyboard types the code from the start: before its first frame nothing can take focus.
    LaunchedEffect(Unit) {
        withFrameNanos {}
        runCatching { focus.requestFocus() }
    }
    val cues = LocalCues.current
    val typeDigit = { digit: Char -> if (!taking && code.length < CODE_LENGTH) onCode(code + digit) }
    val delete = { if (!taking && code.isNotEmpty()) onCode(code.dropLast(1)) }
    val join = { if (!taking && code.length == CODE_LENGTH) onJoin() }

    Box(
        modifier
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val digit = DIGIT_KEYS[event.key]
                when {
                    digit != null -> typeDigit(digit).also { cues.play(Cue.KEY) }.let { true }
                    event.key == Key.Backspace -> delete().also { cues.play(Cue.KEY_DELETE) }.let { true }
                    event.key == Key.Enter || event.key == Key.NumPadEnter -> join().let { true }
                    else -> false
                }
            },
    ) {
        Page {
            KvizicText(
                words.codeHint,
                Modifier.fillMaxWidth(),
                style = type.label,
                color = colors.onPageMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(space.md))
            Row(
                Modifier.fillMaxWidth().semantics {
                    contentDescription =
                        words.codeHint + ": " + code.toList().joinToString(" ")
                },
                horizontalArrangement = Arrangement.spacedBy(space.xs, Alignment.CenterHorizontally),
            ) {
                repeat(CODE_LENGTH) { i ->
                    if (i == CODE_LENGTH / 2) Spacer(Modifier.size(space.sm))
                    Panel(Modifier.size(space.flap.large), kind = PanelKind.WELL, padding = space.xxs) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            KvizicText(code.getOrNull(i)?.toString() ?: "", style = type.digits)
                        }
                    }
                }
            }
            Spacer(Modifier.height(space.md))
            // What the room is set to, before the player is in it.
            when (preview) {
                RoomPreview.None -> {
                    Unit
                }

                RoomPreview.Looking -> {
                    LoadingSpinner(name = strings.loading, modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                is RoomPreview.Found -> {
                    RoomCard(preview.lobby, topics)
                }

                is RoomPreview.Missing -> {
                    if (preview.error == GameError.LOBBY_NOT_FOUND) {
                        KvizicText(
                            words.roomNotFound,
                            Modifier.fillMaxWidth(),
                            color = colors.loss,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(Modifier.height(space.md))
            if (entry is Entry.Failed && entry.way == EntryWay.JOIN) {
                Notice(strings.entryFailureText(entry.error, entry.retryAfter), onDismiss = onDismissFailure)
                Spacer(Modifier.height(space.md))
            }
            Spacer(Modifier.weight(1f))
            KEYPAD.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    row.forEach { key ->
                        when (key) {
                            DELETE -> {
                                StageButton(
                                    words.deleteDigit,
                                    onClick = tapped("join.delete", onClick = delete),
                                    modifier = Modifier.weight(1f),
                                    kind = ButtonKind.DARK,
                                    icon = KvizicIcons.Back,
                                    enabled = !taking && code.isNotEmpty(),
                                    cue = Cue.KEY_DELETE,
                                )
                            }

                            JOIN -> {
                                StageButton(
                                    if (taking) words.entering else words.join,
                                    onClick = tapped("join.join", onClick = join),
                                    modifier = Modifier.weight(1f),
                                    enabled = !taking && code.length == CODE_LENGTH,
                                )
                            }

                            else -> {
                                StageButton(
                                    key.toString(),
                                    onClick = tapped("join.digit") { typeDigit(key) },
                                    modifier = Modifier.weight(1f),
                                    kind = ButtonKind.SECONDARY,
                                    enabled = !taking && code.length < CODE_LENGTH,
                                    cue = Cue.KEY,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(space.sm))
            }
        }
    }
}

private const val DELETE = '<'
private const val JOIN = '>'

/** The keypad as a phone's: 1 to 9 in rows of three, then delete, 0 and Join. */
private val KEYPAD = listOf("123", "456", "789", "$DELETE" + "0" + "$JOIN").map { it.toList() }

private val DIGIT_KEYS: Map<Key, Char> =
    mapOf(
        Key.Zero to '0',
        Key.One to '1',
        Key.Two to '2',
        Key.Three to '3',
        Key.Four to '4',
        Key.Five to '5',
        Key.Six to '6',
        Key.Seven to '7',
        Key.Eight to '8',
        Key.Nine to '9',
        Key.NumPad0 to '0',
        Key.NumPad1 to '1',
        Key.NumPad2 to '2',
        Key.NumPad3 to '3',
        Key.NumPad4 to '4',
        Key.NumPad5 to '5',
        Key.NumPad6 to '6',
        Key.NumPad7 to '7',
        Key.NumPad8 to '8',
        Key.NumPad9 to '9',
    )
