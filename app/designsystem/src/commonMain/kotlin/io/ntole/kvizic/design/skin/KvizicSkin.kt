package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.intl.LocaleList
import io.ntole.kvizic.design.font.family

/**
 * The script the game is shown in, which sets the locale its text is shaped for, so the fonts draw the
 * Serbian forms where the platform lets them, and the letters its answers are named by.
 */
enum class Script(
    val languageTag: String,
    private val alphabet: List<String>,
) {
    CYRILLIC("sr-Cyrl", "А Б В Г Д Ђ Е Ж З И Ј К Л Љ М Н Њ О П Р С Т Ћ У Ф Х Ц Ч Џ Ш".split(' ')),

    /** A B C D, as a Latin quiz names its answers, rather than the Cyrillic's A B V G transliterated. */
    LATIN("sr-Latn", "A B C D E F G H I J K L M N O P Q R S T U V W X Y Z".split(' ')),
    ;

    /** The letter the answer at [index] is named by; past the alphabet's end, two letters. */
    fun letter(index: Int): String {
        require(index >= 0) { "an answer's index is not negative: $index" }
        val size = alphabet.size
        return if (index < size) alphabet[index] else alphabet[index / size - 1] + alphabet[index % size]
    }
}

val LocalSkin = staticCompositionLocalOf { Skins.Default }

val LocalScript = staticCompositionLocalOf { Script.CYRILLIC }

/** The colour text and icons are drawn in where nothing says otherwise, set by what they stand on. */
val LocalContentColor = compositionLocalOf { Color.Unspecified }

/** The style text is set in where nothing says otherwise. */
val LocalTextStyle = compositionLocalOf<SkinTextStyle?> { null }

internal val LocalSkinType =
    staticCompositionLocalOf<SkinType> { error("Draw inside KvizicSkin, which loads the skin's fonts.") }

/**
 * Wears [skin] over [content], in [script]: loads the skin's two faces, sets every style in them for
 * the script's locale, and gives the content the page's text colour. Every screen and component draws
 * inside one.
 */
@Composable
fun KvizicSkin(
    skin: Skin = Skins.Default,
    script: Script = Script.CYRILLIC,
    content: @Composable () -> Unit,
) {
    val display = skin.fonts.display.family()
    val body = skin.fonts.body.family()
    val type =
        remember(skin.type, skin.fonts, display, body, script) {
            SkinType.of(skin.type, skin.fonts.display, display, skin.fonts.body, body, LocaleList(script.languageTag))
        }
    CompositionLocalProvider(
        LocalSkin provides skin,
        LocalSkinType provides type,
        LocalScript provides script,
        LocalContentColor provides skin.colors.onPage,
        LocalTextStyle provides type.body,
        content = content,
    )
}

/** The skin worn, and its tokens, as screens and components read them. */
object KvizicTheme {
    val skin: Skin
        @Composable @ReadOnlyComposable
        get() = LocalSkin.current

    val colors: SkinColors
        @Composable @ReadOnlyComposable
        get() = LocalSkin.current.colors

    val space: SkinSpace
        @Composable @ReadOnlyComposable
        get() = LocalSkin.current.space

    val shapes: SkinShapes
        @Composable @ReadOnlyComposable
        get() = LocalSkin.current.shapes

    val motion: SkinMotion
        @Composable @ReadOnlyComposable
        get() = LocalSkin.current.motion

    val parts: SkinParts
        @Composable @ReadOnlyComposable
        get() = LocalSkin.current.parts

    val type: SkinType
        @Composable @ReadOnlyComposable
        get() = LocalSkinType.current

    val script: Script
        @Composable @ReadOnlyComposable
        get() = LocalScript.current
}
