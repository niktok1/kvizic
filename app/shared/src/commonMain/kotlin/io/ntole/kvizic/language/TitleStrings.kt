package io.ntole.kvizic.language

/** The words of the titles a player earns by achievements, as [Strings.titles]. */
data class TitleStrings(
    /** The profile's heading over the achievements and their titles. */
    val heading: String,
    /** Home's line for a title just earned, `{0}` its words. */
    val newTitle: String,
    /** How far through an achievement the player is, to a screen reader: `{0}` of `{1}`. */
    val progress: String,
) {
    internal fun map(transform: (String) -> String): TitleStrings =
        TitleStrings(heading = transform(heading), newTitle = transform(newTitle), progress = transform(progress))
}

internal val SerbianCyrillicTitleStrings: TitleStrings =
    TitleStrings(heading = "Титуле", newTitle = "Нова титула: {0}", progress = "{0} од {1}")

internal val EnglishTitleStrings: TitleStrings =
    TitleStrings(heading = "Titles", newTitle = "New title: {0}", progress = "{0} of {1}")

internal val CroatianTitleStrings: TitleStrings =
    TitleStrings(heading = "Titule", newTitle = "Nova titula: {0}", progress = "{0} od {1}")

internal val BosnianTitleStrings: TitleStrings =
    TitleStrings(heading = "Titule", newTitle = "Nova titula: {0}", progress = "{0} od {1}")

internal val SlovenianTitleStrings: TitleStrings =
    TitleStrings(heading = "Nazivi", newTitle = "Nov naziv: {0}", progress = "{0} od {1}")

internal val MacedonianTitleStrings: TitleStrings =
    TitleStrings(heading = "Титули", newTitle = "Нова титула: {0}", progress = "{0} од {1}")
