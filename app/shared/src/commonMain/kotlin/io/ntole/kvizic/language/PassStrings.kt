package io.ntole.kvizic.language

/** The words of the button a player passes a question with, as [Strings.pass]. */
data class PassStrings(
    /** What the button, an icon alone, says to a screen reader: the player does not know the answer. */
    val dontKnow: String,
) {
    internal fun map(transform: (String) -> String): PassStrings = PassStrings(dontKnow = transform(dontKnow))
}

internal val SerbianCyrillicPassStrings: PassStrings = PassStrings(dontKnow = "Не знам")

internal val EnglishPassStrings: PassStrings = PassStrings(dontKnow = "I don't know")

internal val CroatianPassStrings: PassStrings = PassStrings(dontKnow = "Ne znam")

internal val BosnianPassStrings: PassStrings = PassStrings(dontKnow = "Ne znam")

internal val SlovenianPassStrings: PassStrings = PassStrings(dontKnow = "Ne vem")

internal val MacedonianPassStrings: PassStrings = PassStrings(dontKnow = "Не знам")
