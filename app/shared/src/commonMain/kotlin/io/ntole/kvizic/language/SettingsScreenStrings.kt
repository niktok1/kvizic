package io.ntole.kvizic.language

/**
 * The words of the Settings screen, as [Strings.settingsScreen]: made in each language as the rest of
 * [Strings] is, and checked by the same tests. What it holds of the About screen's (the Statistics switch,
 * the account id, deleting the account) keeps its words there.
 */
data class SettingsScreenStrings(
    /** The Settings screen's name, as Home's button to it says. */
    val title: String,
    /** The Sound switch: whether the game makes sound. */
    val sound: String,
    /** The heading over the player's account: its id, and deleting it. */
    val account: String,
) {
    /** These strings with [transform] applied to every one of them, as [Strings.map] asks. */
    internal fun map(transform: (String) -> String): SettingsScreenStrings =
        SettingsScreenStrings(
            title = transform(title),
            sound = transform(sound),
            account = transform(account),
        )
}

/** The source text, written by hand. */
internal val SerbianCyrillicSettingsScreenStrings: SettingsScreenStrings =
    SettingsScreenStrings(
        title = "Подешавања",
        sound = "Звук",
        account = "Налог",
    )

internal val EnglishSettingsScreenStrings: SettingsScreenStrings =
    SettingsScreenStrings(
        title = "Settings",
        sound = "Sound",
        account = "Account",
    )
