package io.ntole.kvizic.core.player

import kotlinx.serialization.Serializable

/** Picks one of the game's avatars, by its id (`Avatars`). */
@Serializable
public data class SetAvatarRequest(
    public val avatarId: String,
)
