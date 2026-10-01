package io.ntole.kvizic.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.LocalContentColor

/**
 * A field to type in, sunk into the page as a well: [label] over it, [placeholder] in it while it is
 * empty, and [supporting] under it, in the loss colour when [error] says what is wrong. [secret] masks what
 * is typed, as a token's field does; [focusRequester], when given, is the field's own. A screen reader hears the label as the field's name and [error] as its
 * error.
 */
@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    secret: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
) {
    val skin = KvizicTheme.skin
    val space = skin.space
    val type = KvizicTheme.type
    val colors = skin.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.xxs)) {
        KvizicText(label, style = type.label, color = colors.onPageMuted)
        Panel(Modifier.fillMaxWidth(), kind = PanelKind.WELL, padding = space.sm) {
            val content = LocalContentColor.current
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                        .semantics {
                            contentDescription = label
                            if (error != null) this.error(error)
                        },
                enabled = enabled,
                singleLine = singleLine,
                textStyle = type.body.style.copy(color = content),
                cursorBrush = SolidColor(colors.focus),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                decorationBox = { field ->
                    Box {
                        if (value.isEmpty() && placeholder != null) {
                            KvizicText(placeholder, style = type.body, color = content.copy(alpha = PLACEHOLDER_ALPHA))
                        }
                        field()
                    }
                },
            )
        }
        val note = error ?: supporting
        if (note != null) {
            KvizicText(note, style = type.caption, color = if (error != null) colors.loss else colors.onPageMuted)
        }
    }
}

/** How faint a placeholder is against what is typed. */
private const val PLACEHOLDER_ALPHA = 0.5f
