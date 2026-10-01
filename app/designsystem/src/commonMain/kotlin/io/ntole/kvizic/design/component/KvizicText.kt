package io.ntole.kvizic.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.LocalContentColor
import io.ntole.kvizic.design.skin.LocalTextStyle
import io.ntole.kvizic.design.skin.SkinTextStyle

/**
 * Text in the skin's type: [style], one of `KvizicTheme.type`'s, which also decides whether the text is
 * set in capitals, drawn so and read to a screen reader as written; in [color], or else the colour of what it stands on; underlined, a link, by [textDecoration].
 */
@Composable
fun KvizicText(
    text: String,
    modifier: Modifier = Modifier,
    style: SkinTextStyle = LocalTextStyle.current ?: KvizicTheme.type.body,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    autoSize: TextAutoSize? = null,
    textDecoration: TextDecoration? = null,
) {
    val tint = if (color.isSpecified) color else LocalContentColor.current
    if (style.caps) {
        // Drawn in capitals, read as written: a screen reader may spell out a word in capitals.
        Box(modifier.semantics { this.text = AnnotatedString(text) }, propagateMinConstraints = true) {
            SetText(
                style.apply(text),
                Modifier.clearAndSetSemantics {
                },
                style,
                tint,
                textAlign,
                maxLines,
                minLines,
                overflow,
                autoSize,
                textDecoration,
            )
        }
    } else {
        SetText(text, modifier, style, tint, textAlign, maxLines, minLines, overflow, autoSize, textDecoration)
    }
}

@Composable
private fun SetText(
    text: String,
    modifier: Modifier,
    style: SkinTextStyle,
    tint: Color,
    textAlign: TextAlign?,
    maxLines: Int,
    minLines: Int,
    overflow: TextOverflow,
    autoSize: TextAutoSize?,
    textDecoration: TextDecoration?,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style =
            style.style.copy(
                color = tint,
                textAlign = textAlign ?: style.style.textAlign,
                textDecoration = textDecoration ?: style.style.textDecoration,
            ),
        // Text sized to fit must be able to overflow, or its ellipsis would pass for a fit at the largest size.
        overflow = if (autoSize != null) TextOverflow.Clip else overflow,
        maxLines = maxLines,
        minLines = minLines,
        autoSize = autoSize,
    )
}
