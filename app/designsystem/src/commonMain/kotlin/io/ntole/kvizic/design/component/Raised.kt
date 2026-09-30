package io.ntole.kvizic.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ntole.kvizic.design.skin.SkinDepth
import io.ntole.kvizic.design.skin.SkinMotion
import io.ntole.kvizic.design.skin.SurfaceCache
import io.ntole.kvizic.design.skin.SurfaceColors
import io.ntole.kvizic.design.skin.SurfaceLook
import io.ntole.kvizic.design.skin.drawSurface
import io.ntole.kvizic.design.skin.expandedBy
import io.ntole.kvizic.design.skin.reserve
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * This element standing on a raised surface of [look], drawn by the skin's [depth], its content on the
 * surface's face: the surface's room under it is kept out of the content's layout, so a press moves the
 * face, and whatever is on it, and nothing else.
 *
 * The surface sinks while [interactionSource] reports a press, and settles into a new look, a tile locked
 * in or lit, over [motion]'s `settle`. It does both in the draw phase alone: its node collects the
 * presses itself and animates heights and colours that only its draw reads, so a frame of either
 * composes nothing, lays nothing out and changes no semantics.
 */
internal fun Modifier.raised(
    look: SurfaceLook,
    depth: SkinDepth,
    motion: SkinMotion,
    interactionSource: InteractionSource? = null,
    focus: Color = Color.Unspecified,
    focusWidth: Dp = 0.dp,
): Modifier {
    val (right, bottom) = depth.reserve(look)
    return this then RaisedElement(look, depth, motion, interactionSource, focus, focusWidth) then
        Modifier.absolutePadding(right = right, bottom = bottom)
}

private data class RaisedElement(
    val look: SurfaceLook,
    val depth: SkinDepth,
    val motion: SkinMotion,
    val interactionSource: InteractionSource?,
    val focus: Color,
    val focusWidth: Dp,
) : ModifierNodeElement<RaisedNode>() {
    override fun create(): RaisedNode = RaisedNode(look, depth, motion, interactionSource, focus, focusWidth)

    override fun update(node: RaisedNode) = node.update(look, depth, motion, interactionSource, focus, focusWidth)

    override fun InspectorInfo.inspectableProperties() {
        name = "raised"
        properties["look"] = look
    }
}

private class RaisedNode(
    private var look: SurfaceLook,
    private var depth: SkinDepth,
    private var motion: SkinMotion,
    private var interactionSource: InteractionSource?,
    private var focus: Color,
    private var focusWidth: Dp,
) : Modifier.Node(),
    DrawModifierNode {
    /** The look being left, while [mix] runs from it to [look]. */
    private var from: SurfaceLook = look
    private var mix = Animatable(1f)
    private var lift = Animatable(look.rest.value)
    private var pressed = false
    private var focused = false
    private var watching: Job? = null
    private var pressing: Job? = null
    private var settling: Job? = null
    private val cache = SurfaceCache()

    override fun onAttach() {
        watch()
    }

    override fun onDetach() {
        pressed = false
        focused = false
    }

    fun update(
        look: SurfaceLook,
        depth: SkinDepth,
        motion: SkinMotion,
        interactionSource: InteractionSource?,
        focus: Color,
        focusWidth: Dp,
    ) {
        this.depth = depth
        this.motion = motion
        this.focus = focus
        this.focusWidth = focusWidth
        if (interactionSource != this.interactionSource) {
            this.interactionSource = interactionSource
            watching?.cancel()
            setPressed(false)
            if (isAttached) watch()
        }
        if (look != this.look) {
            val before = this.look
            this.look = look
            if (isAttached) {
                // Settle from the colours the surface shows now, even mid-way through a settle before.
                from = if (mix.value >= 1f) before else from.mixedInto(before, mix.value)
                coroutineScope.launch {
                    mix.snapTo(0f)
                    mix.animateTo(1f, tween(motion.settle, easing = FastOutSlowInEasing))
                }
                settle(afterPress = false)
            } else {
                from = look
                mix = Animatable(1f)
                lift = Animatable(look.rest.value)
            }
        }
        invalidateDraw()
    }

    private fun watch() {
        val source = interactionSource ?: return
        watching =
            coroutineScope.launch {
                val presses = mutableListOf<PressInteraction.Press>()
                val focuses = mutableListOf<FocusInteraction.Focus>()
                source.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> presses += interaction
                        is PressInteraction.Release -> presses -= interaction.press
                        is PressInteraction.Cancel -> presses -= interaction.press
                        is FocusInteraction.Focus -> focuses += interaction
                        is FocusInteraction.Unfocus -> focuses -= interaction.focus
                    }
                    setPressed(presses.isNotEmpty())
                    if (focused != focuses.isNotEmpty()) {
                        focused = focuses.isNotEmpty()
                        invalidateDraw()
                    }
                }
            }
    }

    private fun setPressed(now: Boolean) {
        if (pressed == now || !isAttached) {
            pressed = now
            return
        }
        pressed = now
        if (now) {
            settling?.cancel()
            pressing =
                coroutineScope.launch { lift.animateTo(look.pressed.value, tween(motion.press)) }
        } else {
            settle(afterPress = true)
        }
    }

    /**
     * Brings the surface to its height at rest once any press has sunk it all the way, so even the
     * quickest tap is felt as a whole press: on a spring [afterPress], as a key comes back up, and
     * otherwise over the skin's settle, a tile locking in or going dark.
     */
    private fun settle(afterPress: Boolean) {
        val press = pressing
        settling?.cancel()
        settling =
            coroutineScope.launch {
                press?.join()
                if (pressed) return@launch
                val spec: AnimationSpec<Float> =
                    if (afterPress) {
                        spring(dampingRatio = motion.releaseDamping, stiffness = motion.releaseStiffness)
                    } else {
                        tween(motion.settle, easing = FastOutSlowInEasing)
                    }
                lift.animateTo(look.rest.value, spec)
            }
    }

    override fun ContentDrawScope.draw() {
        val t = mix.value
        val target = look
        val colors =
            if (t >= 1f) {
                SurfaceColors(target.fill, target.side, target.outline, target.glow)
            } else {
                val mixed = from.mixedInto(target, t)
                SurfaceColors(mixed.fill, mixed.side, mixed.outline, mixed.glow)
            }
        val face = drawSurface(depth, target, colors, lift.value.dp.toPx(), cache)
        translate(face.x, face.y) {
            this@draw.drawContent()
            val ring = focusWidth.toPx()
            if (focused && focus.isSpecified && ring > 0f) {
                cache.outline?.let { outline ->
                    drawOutline(outline.expandedBy(ring), focus, style = Stroke(width = ring))
                }
            }
        }
    }

    private fun SurfaceLook.mixedInto(
        other: SurfaceLook,
        t: Float,
    ): SurfaceLook =
        other.copy(
            fill = lerp(fill, other.fill, t),
            side = lerp(side, other.side, t),
            outline = lerp(outline, other.outline, t),
            glow = mixedGlow(glow, other.glow, t),
        )

    private fun mixedGlow(
        a: Color,
        b: Color,
        t: Float,
    ): Color =
        when {
            a.isSpecified && b.isSpecified -> lerp(a, b, t)
            b.isSpecified -> b.copy(alpha = b.alpha * t)
            a.isSpecified -> a.copy(alpha = a.alpha * (1 - t))
            else -> Color.Unspecified
        }
}
