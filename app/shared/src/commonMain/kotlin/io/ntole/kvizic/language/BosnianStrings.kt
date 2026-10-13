package io.ntole.kvizic.language

/*
 * Bosnian (bs), as [Language.BOSNIAN] shows the game: for now a placeholder, the words of
 * Serbian Latin, until its own are written by hand. Each part of [Strings] is a value of its own here, so
 * writing them changes this file alone; [PENDING_LANGUAGES] names Bosnian until then. The parts come first:
 * a file's values are made in its order.
 */

internal val BosnianAboutStrings: AboutStrings = SerbianLatinStrings.aboutScreen

internal val BosnianSettingsScreenStrings: SettingsScreenStrings = SerbianLatinStrings.settingsScreen

internal val BosnianUpdateStrings: UpdateStrings = SerbianLatinStrings.updateScreen

internal val BosnianGameStrings: GameStrings = SerbianLatinStrings.game

/** The game's words in Bosnian. */
val BosnianStrings: Strings =
    SerbianLatinStrings.copy(
        aboutScreen = BosnianAboutStrings,
        settingsScreen = BosnianSettingsScreenStrings,
        updateScreen = BosnianUpdateStrings,
        game = BosnianGameStrings,
    )
