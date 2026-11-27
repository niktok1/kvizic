package io.ntole.kvizic.language

/**
 * The words of the reactions a player sends from a question's reveal, as [Strings.reactions]: made in each
 * language as the rest of [Strings] is, and checked by the same tests.
 */
data class ReactionStrings(
    /** The round button on the reveal that opens the reactions, as a screen reader hears it. */
    val react: String,
) {
    internal fun map(transform: (String) -> String): ReactionStrings = ReactionStrings(react = transform(react))
}

/** The source text, written by hand. */
internal val SerbianCyrillicReactionStrings: ReactionStrings = ReactionStrings(react = "Реагуј")

internal val EnglishReactionStrings: ReactionStrings = ReactionStrings(react = "React")
