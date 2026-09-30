package io.ntole.kvizic.design.component

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.LocalContentColor
import io.ntole.kvizic.design.skin.LocalTextStyle
import io.ntole.kvizic.design.skin.SkinTextStyle

/**
 * Text in the skin's type: [style], one of `KvizicTheme.type`'s, which also decides whether the text is
 * set in capitals; in [color], or else the colour of what it stands on.
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
) {
    val tint = if (color.isSpecified) color else LocalContentColor.current
    BasicText(
        text = style.apply(text),
        modifier = modifier,
        style =
            style.style.copy(
                color = tint,
                textAlign = textAlign ?: style.style.textAlign,
            ),
        // Text sized to fit must be able to overflow, or its ellipsis would pass for a fit at the largest size.
        overflow = if (autoSize != null) TextOverflow.Clip else overflow,
        maxLines = maxLines,
        minLines = minLines,
        autoSize = autoSize,
    )
}
