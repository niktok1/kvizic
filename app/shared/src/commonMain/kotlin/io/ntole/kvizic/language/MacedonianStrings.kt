package io.ntole.kvizic.language

/*
 * Macedonian (mk), as [Language.MACEDONIAN] shows the game: for now a placeholder, the words of
 * Serbian Cyrillic, until its own are written by hand. Each part of [Strings] is a value of its own here, so
 * writing them changes this file alone; [PENDING_LANGUAGES] names Macedonian until then. The parts come first:
 * a file's values are made in its order.
 */

internal val MacedonianAboutStrings: AboutStrings = SerbianCyrillicStrings.aboutScreen

internal val MacedonianSettingsScreenStrings: SettingsScreenStrings = SerbianCyrillicStrings.settingsScreen

internal val MacedonianUpdateStrings: UpdateStrings = SerbianCyrillicStrings.updateScreen

internal val MacedonianGameStrings: GameStrings = SerbianCyrillicStrings.game

/** The game's words in Macedonian. */
val MacedonianStrings: Strings =
    SerbianCyrillicStrings.copy(
        aboutScreen = MacedonianAboutStrings,
        settingsScreen = MacedonianSettingsScreenStrings,
        updateScreen = MacedonianUpdateStrings,
        game = MacedonianGameStrings,
    )
