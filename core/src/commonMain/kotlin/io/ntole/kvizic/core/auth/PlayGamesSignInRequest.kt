package io.ntole.kvizic.core.auth

import kotlinx.serialization.Serializable

/**
 * Signs in with Google Play Games Services v2: the one-time [serverAuthCode] Play Games gave the app for
 * this server. The server exchanges it with Google for the Play Games player and their name, so nothing
 * the client says about who the player is gets taken on trust. [toString] hides the code.
 */
@Serializable
public data class PlayGamesSignInRequest(
    public val serverAuthCode: String,
) {
    public override fun toString(): String = "PlayGamesSignInRequest(serverAuthCode=***)"
}
