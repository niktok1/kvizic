package io.ntole.kvizic.core.player

import kotlinx.serialization.Serializable

/**
 * A moderator deleting a player's account at their request, naming them by [accountId], the player id
 * the game shows on its About screen.
 */
@Serializable
public data class DeleteAccountRequest(
    public val accountId: String,
)
