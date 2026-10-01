package io.ntole.kvizic.language

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.ntole.kvizic.core.domain.language.SerbianScript

/**
 * Every word of the game's own, one value per [Language]: a screen reads them from [LocalStrings] and
 * writes none of its own.
 *
 * Plain Kotlin values rather than compose resources: three languages, one of them made from another by a
 * function, which string resources cannot express, and every text checked by the compiler, a missing one
 * being a constructor that does not compile rather than a key that fails at run time.
 */
data class Strings(
    /** The game's name. */
    val gameName: String,
    /** The button back to the screen before. */
    val back: String,
    /** The button under a failure, on every screen that has one. One text, so the game says it one way. */
    val tryAgain: String,
    /** The button that closes without doing anything, as a confirm dialog's. */
    val cancel: String,
    /** A spinner's name, for a screen reader. */
    val loading: String,
    /**
     * The one line under a spinner that has turned for a while: a sleeping server's first request takes up
     * to a minute to wake it, so the player knows nothing is stuck.
     */
    val stillLoading: String,
    /** A failure: the device is offline. */
    val offline: String,
    /** A failure: too many tries, and the server said to wait `{0}` seconds, less than a minute. */
    val tooManyTries: String,
    /** A failure: too many tries, and the server said to wait a minute or more, `{0}` minutes rounded up. */
    val tooManyTriesMinutes: String,
    /** A failure: too many tries, and the server named no wait. */
    val tooManyTriesNoWait: String,
    /** Any other failure, which asks in [tryAgain]'s own words. */
    val somethingWrong: String,
    /** The About screen's words. */
    val aboutScreen: AboutStrings,
    /** The words of the screen shown once the server serves this build nothing more. */
    val updateScreen: UpdateStrings,
    /** The game's own words, from Home to the results. */
    val game: GameStrings,
) {
    /**
     * These strings with [transform] applied to every one of them, which is how Serbian Latin is made. A
     * text added to [Strings] goes through here too, or [SerbianLatinStrings] would leave it in Cyrillic,
     * which `StringsTest` catches.
     */
    internal fun map(transform: (String) -> String): Strings =
        Strings(
            gameName = transform(gameName),
            back = transform(back),
            tryAgain = transform(tryAgain),
            cancel = transform(cancel),
            loading = transform(loading),
            stillLoading = transform(stillLoading),
            offline = transform(offline),
            tooManyTries = transform(tooManyTries),
            tooManyTriesMinutes = transform(tooManyTriesMinutes),
            tooManyTriesNoWait = transform(tooManyTriesNoWait),
            somethingWrong = transform(somethingWrong),
            aboutScreen = aboutScreen.map(transform),
            updateScreen = updateScreen.map(transform),
            game = game.map(transform),
        )
}

/** The source text, written by hand. */
val SerbianCyrillicStrings: Strings =
    Strings(
        gameName = "Квизић",
        back = "Назад",
        tryAgain = "Покушај поново",
        cancel = "Откажи",
        loading = "Учитавање",
        stillLoading = "Још мало…",
        offline = "Нема интернет везе.",
        tooManyTries = "Превише покушаја. Сачекај {0} сек.",
        tooManyTriesMinutes = "Превише покушаја. Сачекај {0} мин.",
        tooManyTriesNoWait = "Превише покушаја. Сачекај мало.",
        somethingWrong = "Нешто није у реду. Покушај поново.",
        aboutScreen = SerbianCyrillicAboutStrings,
        updateScreen = SerbianCyrillicUpdateStrings,
        game = SerbianCyrillicGameStrings,
    )

/** Made from [SerbianCyrillicStrings], never written by hand, so the two cannot say different things. */
val SerbianLatinStrings: Strings = SerbianCyrillicStrings.map(SerbianScript::toLatin)

val EnglishStrings: Strings =
    Strings(
        gameName = "Kvizić",
        back = "Back",
        tryAgain = "Try again",
        cancel = "Cancel",
        loading = "Loading",
        stillLoading = "Just a moment…",
        offline = "No connection. Check your internet.",
        tooManyTries = "Too many tries. Wait {0} s.",
        tooManyTriesMinutes = "Too many tries. Wait {0} min.",
        tooManyTriesNoWait = "Too many tries. Wait a moment.",
        somethingWrong = "Something went wrong. Try again.",
        aboutScreen = EnglishAboutStrings,
        updateScreen = EnglishUpdateStrings,
        game = EnglishGameStrings,
    )

/** The strings [language] is written in. */
fun stringsOf(language: Language): Strings =
    when (language) {
        Language.SERBIAN_CYRILLIC -> SerbianCyrillicStrings
        Language.SERBIAN_LATIN -> SerbianLatinStrings
        Language.ENGLISH -> EnglishStrings
    }

/** The strings of the language the game is shown in; Serbian Cyrillic, the default, until one is provided. */
val LocalStrings = staticCompositionLocalOf { stringsOf(Language.DEFAULT) }

/**
 * The language the game is shown in, for the words that are not [Strings] but the server's: a topic's
 * name, a player's name in Latin. Serbian Cyrillic, the default, until one is provided.
 */
val LocalLanguage = staticCompositionLocalOf { Language.DEFAULT }

/** Shows [content] in [language]: everything under it reads [LocalStrings] in that language, and [LocalLanguage] names it. */
@Composable
fun KvizicStrings(
    language: Language,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStrings provides stringsOf(language),
        LocalLanguage provides language,
        content = content,
    )
}
