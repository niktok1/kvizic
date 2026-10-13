package io.ntole.kvizic.language

import io.ntole.kvizic.core.domain.region.Country

/**
 * The words of the Settings screen, as [Strings.settingsScreen]: made in each language as the rest of
 * [Strings] is, and checked by the same tests.
 */
data class SettingsScreenStrings(
    /** The Settings screen's name, as Home's button to it says. */
    val title: String,
    /** The Sound switch: whether the game makes sound. */
    val sound: String,
    /** The countries a player may play from, each on its chip. */
    val countries: CountryNames,
    /**
     * What a screen reader says of the country chips, which have no heading, as the language chips have none:
     * that they say where the player plays from, which decides the questions about a country they get.
     */
    val countrySaid: String,
) {
    /** These strings with [transform] applied to every one of them, as [Strings.map] asks. */
    internal fun map(transform: (String) -> String): SettingsScreenStrings =
        SettingsScreenStrings(
            title = transform(title),
            sound = transform(sound),
            countries = countries.map(transform),
            countrySaid = transform(countrySaid),
        )
}

/**
 * The countries' names, as [SettingsScreenStrings.countries]: a field each, so a language missing one does not
 * compile.
 */
data class CountryNames(
    val serbia: String,
    val croatia: String,
    /** Bosnia and Herzegovina. */
    val bosnia: String,
    val slovenia: String,
    /** North Macedonia. */
    val macedonia: String,
    val montenegro: String,
) {
    /** The name of [country], or none for one this build does not know, which no chip offers. */
    fun of(country: Country): String? =
        when (country) {
            Country.RS -> serbia
            Country.HR -> croatia
            Country.BA -> bosnia
            Country.SI -> slovenia
            Country.MK -> macedonia
            Country.ME -> montenegro
            Country.UNKNOWN -> null
        }

    /** These names with [transform] applied to every one of them, as [Strings.map] asks. */
    internal fun map(transform: (String) -> String): CountryNames =
        CountryNames(
            serbia = transform(serbia),
            croatia = transform(croatia),
            bosnia = transform(bosnia),
            slovenia = transform(slovenia),
            macedonia = transform(macedonia),
            montenegro = transform(montenegro),
        )
}

/** The source text, written by hand. */
internal val SerbianCyrillicSettingsScreenStrings: SettingsScreenStrings =
    SettingsScreenStrings(
        title = "Подешавања",
        sound = "Звук",
        countries =
            CountryNames(
                serbia = "Србија",
                croatia = "Хрватска",
                bosnia = "Босна и Херцеговина",
                slovenia = "Словенија",
                macedonia = "Северна Македонија",
                montenegro = "Црна Гора",
            ),
        countrySaid = "Земља из које играш, по којој добијаш и питања о њој",
    )

internal val EnglishSettingsScreenStrings: SettingsScreenStrings =
    SettingsScreenStrings(
        title = "Settings",
        sound = "Sound",
        countries =
            CountryNames(
                serbia = "Serbia",
                croatia = "Croatia",
                bosnia = "Bosnia and Herzegovina",
                slovenia = "Slovenia",
                macedonia = "North Macedonia",
                montenegro = "Montenegro",
            ),
        countrySaid = "The country you play from, which also brings questions about it",
    )
