package io.ntole.kvizic.core.player

import kotlinx.serialization.Serializable

/**
 * An achievement and the title it earns, [id], as far as the player has come: [progress] of [goal], [earned] once
 * reached and kept from then on. [title] and [how] (what earns it) are words by content language
 * (`ContentLanguages`), `en` too. The server holds the list, so a goal or an achievement changes with no release.
 */
@Serializable
public data class AchievementDto(
    public val id: String,
    public val title: Map<String, String> = emptyMap(),
    public val how: Map<String, String> = emptyMap(),
    public val progress: Int = 0,
    public val goal: Int = 1,
    public val earned: Boolean = false,
)
