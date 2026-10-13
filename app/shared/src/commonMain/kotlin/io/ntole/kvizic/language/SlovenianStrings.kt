package io.ntole.kvizic.language

/*
 * Slovenian (sl), as [Language.SLOVENIAN] shows the game: for now a placeholder, the words of
 * Serbian Latin, until its own are written by hand. Each part of [Strings] is a value of its own here, so
 * writing them changes this file alone; [PENDING_LANGUAGES] names Slovenian until then. The parts come first:
 * a file's values are made in its order.
 */

internal val SlovenianAboutStrings: AboutStrings = SerbianLatinStrings.aboutScreen

internal val SlovenianSettingsScreenStrings: SettingsScreenStrings = SerbianLatinStrings.settingsScreen

internal val SlovenianUpdateStrings: UpdateStrings = SerbianLatinStrings.updateScreen

internal val SlovenianGameStrings: GameStrings = SerbianLatinStrings.game

/** The game's words in Slovenian. */
val SlovenianStrings: Strings =
    SerbianLatinStrings.copy(
        aboutScreen = SlovenianAboutStrings,
        settingsScreen = SlovenianSettingsScreenStrings,
        updateScreen = SlovenianUpdateStrings,
        game = SlovenianGameStrings,
    )
