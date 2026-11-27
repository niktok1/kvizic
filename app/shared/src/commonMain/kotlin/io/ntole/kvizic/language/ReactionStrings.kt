package io.ntole.kvizic.language

/**
 * The words of the reactions a player sends from a question's reveal and the results, as [Strings.reactions]: made in each
 * language as the rest of [Strings] is, and checked by the same tests.
 */
data class ReactionStrings(
    /** The round button on the reveal and the results that opens the reactions, as a screen reader hears it. */
    val react: String,
    /** The same button on the results once the reactions are open, which puts them away. */
    val close: String,
) {
    internal fun map(transform: (String) -> String): ReactionStrings =
        ReactionStrings(react = transform(react), close = transform(close))
}

/** The source text, written by hand. */
internal val SerbianCyrillicReactionStrings: ReactionStrings = ReactionStrings(react = "Реагуј", close = "Затвори")

internal val EnglishReactionStrings: ReactionStrings = ReactionStrings(react = "React", close = "Close")
