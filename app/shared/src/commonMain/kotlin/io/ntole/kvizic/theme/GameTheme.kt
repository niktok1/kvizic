package io.ntole.kvizic.theme

import androidx.compose.runtime.Composable
import io.ntole.kvizic.design.skin.KvizicSkin
import io.ntole.kvizic.design.skin.Script
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.language.KvizicStrings
import io.ntole.kvizic.language.Language

/**
 * The game's look and words in [language]: [skin] worn, its answers lettered in the language's script, and
 * the strings under it. Every screen draws inside one, the app's and a test's alike.
 */
@Composable
fun GameTheme(
    language: Language,
    skin: Skin = Skins.Default,
    content: @Composable () -> Unit,
) {
    KvizicSkin(skin, scriptOf(language)) { KvizicStrings(language, content) }
}

/** The letters a question's answers are named by in [language]: Cyrillic's А Б В Г, or a Latin quiz's A B C D. */
internal fun scriptOf(language: Language): Script =
    when (language) {
        Language.SERBIAN_CYRILLIC -> Script.CYRILLIC
        Language.SERBIAN_LATIN, Language.ENGLISH -> Script.LATIN
    }
