package io.ntole.kvizic.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.LocalContentColor

/**
 * One of [KvizicIcons][io.ntole.kvizic.design.icon.KvizicIcons], tinted whole in [tint], or else in the
 * colour of what it stands on, [size] square. Named for a screen reader by [contentDescription]; null
 * for an icon that only decorates a word beside it.
 */
@Composable
fun KvizicIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = Dp.Unspecified,
) {
    val colour = if (tint.isSpecified) tint else LocalContentColor.current
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(if (size.isSpecified) size else KvizicTheme.space.icon.medium),
        colorFilter = ColorFilter.tint(colour),
    )
}
