package io.ntole.kvizic.core.domain.player

/**
 * An achievement and the title it earns, [id], as far as the player has come: [progress] of [goal], [earned]
 * once reached. [title] and [how] (what earns it) are the server's words by content language, `en` too.
 */
public data class Achievement(
    public val id: String,
    public val title: Map<String, String>,
    public val how: Map<String, String>,
    public val progress: Int,
    public val goal: Int,
    public val earned: Boolean,
)
