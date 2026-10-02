package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import io.ntole.kvizic.design.font.Face

/** Which of a skin's two faces a style is set in. */
enum class FontRole { DISPLAY, BODY }

/**
 * One text style as a skin writes it: the face and weight, the size and line, the spacing in em, and
 * whether the skin sets it in capitals, which is a skin's choice and not a screen's: Buzzers' buttons
 * shout, Notebook's are written.
 */
@Immutable
data class TypeSpec(
    val role: FontRole,
    val weight: FontWeight,
    val size: TextUnit,
    val lineHeight: TextUnit,
    val tracking: TextUnit = 0.em,
    val caps: Boolean = false,
    /** OpenType features, as CSS writes them: `tnum` for figures of one width. */
    val features: String? = null,
)

/** Every style a skin sets text in. */
@Immutable
data class SkinTypeScale(
    /** The game's name on its sign. */
    val logo: TypeSpec,
    /** The hero button's word: Брза игра. */
    val hero: TypeSpec,
    val headline: TypeSpec,
    /**
     * A question over its answers' tiles, as large as it fits, and the least it shrinks to: a long question is
     * set smaller, never cut, down to [questionMin].
     */
    val question: TypeSpec,
    val questionMin: TypeSpec,
    /** An answer on its tile, as large as it fits, and the least it shrinks to. */
    val answer: TypeSpec,
    val answerMin: TypeSpec,
    /** The letter on a tile's mark. */
    val letter: TypeSpec,
    val button: TypeSpec,
    /** A small button's word, and a quiet one's: Соло, Пријави питање. */
    val buttonSmall: TypeSpec,
    /** A small label over a section, and the words on a chip. */
    val label: TypeSpec,
    val chip: TypeSpec,
    val body: TypeSpec,
    val bodyStrong: TypeSpec,
    val caption: TypeSpec,
    /** A player's name in a seat or a list. */
    val name: TypeSpec,
    /** Points won or lost beside a player. */
    val delta: TypeSpec,
    /** A split-flap digit; its size is the cell's, so only its face and weight count. */
    val digits: TypeSpec,
    /** A digit on a badge: the order of the right answers. */
    val badge: TypeSpec,
)

/** A style ready to set text in, and whether its text is set in capitals. */
@Immutable
data class SkinTextStyle(
    val style: TextStyle,
    val caps: Boolean,
) {
    /** [text] as this style sets it. */
    fun apply(text: String): String = if (caps) text.uppercase() else text
}

/** [SkinTypeScale] with its faces loaded and its locale set: what components set their text in. */
@Immutable
data class SkinType(
    val logo: SkinTextStyle,
    val hero: SkinTextStyle,
    val headline: SkinTextStyle,
    val question: SkinTextStyle,
    val questionMin: SkinTextStyle,
    val answer: SkinTextStyle,
    val answerMin: SkinTextStyle,
    val letter: SkinTextStyle,
    val button: SkinTextStyle,
    val buttonSmall: SkinTextStyle,
    val label: SkinTextStyle,
    val chip: SkinTextStyle,
    val body: SkinTextStyle,
    val bodyStrong: SkinTextStyle,
    val caption: SkinTextStyle,
    val name: SkinTextStyle,
    val delta: SkinTextStyle,
    val digits: SkinTextStyle,
    val badge: SkinTextStyle,
) {
    internal companion object {
        fun of(
            scale: SkinTypeScale,
            display: Face,
            displayFamily: FontFamily,
            body: Face,
            bodyFamily: FontFamily,
            locale: LocaleList,
        ): SkinType {
            fun resolve(spec: TypeSpec): SkinTextStyle {
                val face = if (spec.role == FontRole.DISPLAY) display else body
                val family = if (spec.role == FontRole.DISPLAY) displayFamily else bodyFamily
                return SkinTextStyle(
                    style =
                        TextStyle(
                            fontFamily = family,
                            fontWeight = spec.weight,
                            fontSize = spec.size * face.sizeScale,
                            // A line in em follows the size it is set at, as an answer sized to fit must.
                            lineHeight = spec.lineHeight.let { if (it.isEm) it else it * face.sizeScale },
                            letterSpacing = (spec.tracking.value + face.trackingShift).em,
                            fontFeatureSettings = spec.features,
                            // The Serbian forms of б and italic г д п т: the fonts switch to them by
                            // the locale where the platform passes it to the shaper (the README).
                            localeList = locale,
                            // Only the weights shipped: no faked bold on a face that lacks one.
                            fontSynthesis = FontSynthesis.None,
                            lineHeightStyle =
                                LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None,
                                ),
                        ),
                    caps = spec.caps,
                )
            }
            return SkinType(
                logo = resolve(scale.logo),
                hero = resolve(scale.hero),
                headline = resolve(scale.headline),
                question = resolve(scale.question),
                questionMin = resolve(scale.questionMin),
                answer = resolve(scale.answer),
                answerMin = resolve(scale.answerMin),
                letter = resolve(scale.letter),
                button = resolve(scale.button),
                buttonSmall = resolve(scale.buttonSmall),
                label = resolve(scale.label),
                chip = resolve(scale.chip),
                body = resolve(scale.body),
                bodyStrong = resolve(scale.bodyStrong),
                caption = resolve(scale.caption),
                name = resolve(scale.name),
                delta = resolve(scale.delta),
                digits = resolve(scale.digits),
                badge = resolve(scale.badge),
            )
        }
    }
}
