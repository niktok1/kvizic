package io.ntole.kvizic.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.skin.LocalContentColor

/**
 * One of [KvizicIcons][io.ntole.kvizic.design.icon.KvizicIcons], tinted whole in [tint], or else in the
 * colour of what it stands on, [size] square. Named for a screen reader by [contentDescription]; null
 * for an icon that only decorates a word beside it. [tintInDraw], when given, is the tint instead, read in
 * the draw alone: one on its way to another.
 */
@Composable
fun KvizicIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
    size: Dp = Dp.Unspecified,
    tintInDraw: ColorProducer? = null,
) {
    val painter = rememberVectorPainter(icon)
    val sized = modifier.size(if (size.isSpecified) size else KvizicTheme.space.icon.medium)
    if (tintInDraw != null) {
        Box(
            sized
                .semantics {
                    if (contentDescription != null) {
                        this.contentDescription = contentDescription
                        role = Role.Image
                    }
                }.drawBehind {
                    val area = this.size
                    with(painter) { draw(area, colorFilter = ColorFilter.tint(tintInDraw())) }
                },
        )
        return
    }
    val colour = if (tint.isSpecified) tint else LocalContentColor.current
    Image(
        painter = painter,
        contentDescription = contentDescription,
        modifier = sized,
        colorFilter = ColorFilter.tint(colour),
    )
}
