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
    /** The heading over what the game keeps of the player: the statistics sent, the account's id, deleting it. */
    val data: String,
) {
    /** These strings with [transform] applied to every one of them, as [Strings.map] asks. */
    internal fun map(transform: (String) -> String): SettingsScreenStrings =
        SettingsScreenStrings(
            title = transform(title),
            sound = transform(sound),
            data = transform(data),
        )
}

/** The source text, written by hand. */
internal val SerbianCyrillicSettingsScreenStrings: SettingsScreenStrings =
    SettingsScreenStrings(
        title = "Подешавања",
        sound = "Звук",
        data = "Подаци",
    )

internal val EnglishSettingsScreenStrings: SettingsScreenStrings =
    SettingsScreenStrings(
        title = "Settings",
        sound = "Sound",
        data = "Data",
    )
