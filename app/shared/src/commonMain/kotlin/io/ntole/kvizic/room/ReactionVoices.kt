package io.ntole.kvizic.room

/**
 * Who is heard reacting. A reaction always bursts over its seat, but its sound is rarer: a player is heard
 * once in [perPlayerMillis], whatever else they send, and the room is heard one reaction at a time, so none
 * is sounded within [sharedGapMillis] of another, however many send at once. A reaction not sounded does not
 * count against its player: only one that was heard does.
 *
 * Every player's the same, the player's own among them: their own is sounded from the server's echo, which
 * is what bursts, so a reaction the server dropped is never heard.
 */
internal class ReactionVoices(
    private val nowMillis: () -> Long,
    private val perPlayerMillis: Long = PER_PLAYER_MILLIS,
    private val sharedGapMillis: Long = SHARED_GAP_MILLIS,
) {
    private val heardAt = mutableMapOf<String, Long>()
    private var lastHeardAt: Long? = null

    /** Whether the reaction [player] has just sent is to be sounded; if it is, it counts from now. */
    fun allow(player: String): Boolean {
        val now = nowMillis()
        val theirs = heardAt[player]
        if (theirs != null && now - theirs < perPlayerMillis) return false
        val any = lastHeardAt
        if (any != null && now - any < sharedGapMillis) return false
        heardAt[player] = now
        lastHeardAt = now
        return true
    }

    internal companion object {
        /** A player is heard reacting once in this long: there is no need for more. */
        const val PER_PLAYER_MILLIS = 30_000L

        /** The room's reactions are heard this far apart at the least. */
        const val SHARED_GAP_MILLIS = 400L
    }
}
