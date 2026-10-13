package io.ntole.kvizic.language

/*
 * Croatian (hr), as [Language.CROATIAN] shows the game: for now a placeholder, the words of
 * Serbian Latin, until its own are written by hand. Each part of [Strings] is a value of its own here, so
 * writing them changes this file alone; [PENDING_LANGUAGES] names Croatian until then. The parts come first:
 * a file's values are made in its order.
 */

internal val CroatianAboutStrings: AboutStrings = SerbianLatinStrings.aboutScreen

internal val CroatianSettingsScreenStrings: SettingsScreenStrings = SerbianLatinStrings.settingsScreen

internal val CroatianUpdateStrings: UpdateStrings = SerbianLatinStrings.updateScreen

internal val CroatianGameStrings: GameStrings = SerbianLatinStrings.game

/** The game's words in Croatian. */
val CroatianStrings: Strings =
    SerbianLatinStrings.copy(
        aboutScreen = CroatianAboutStrings,
        settingsScreen = CroatianSettingsScreenStrings,
        updateScreen = CroatianUpdateStrings,
        game = CroatianGameStrings,
    )
