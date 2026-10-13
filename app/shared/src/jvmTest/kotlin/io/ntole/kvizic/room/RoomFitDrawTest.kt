package io.ntole.kvizic.room

import io.ntole.kvizic.language.Language
import kotlin.test.Test

/**
 * The lobby and the room's dialogs with every word whole ([RoomFitChecks]) in Serbian Cyrillic, the source, in
 * every skin, on the phones and the tablet the game was tried on (393 and 800 wide) and on the narrowest at a
 * phone's large font.
 */
class RoomFitDrawTest : RoomFitChecks() {
    @Test
    fun `the lobby's chips are whole with every setting under a long name`() =
        lobbyChipsWhole(Language.DEFAULT, LOBBY_SIZES)

    @Test
    fun `the host's choices about a member are whole on a phone and a tablet`() =
        memberChoicesWhole(Language.DEFAULT, DIALOG_SIZES)

    @Test
    fun `a member's vote to put a player out is whole on the narrowest phone`() = voteWhole(Language.DEFAULT)

    @Test
    fun `every reason to report a question is read whole on the narrowest phone`() = reportWhole(Language.DEFAULT)

    private companion object {
        /** The phone the chips were crushed on, the narrowest, and the narrowest at a large font. */
        val LOBBY_SIZES = listOf(393 to 1f, WIDTH to 1f, WIDTH to LARGE_FONT)

        /** The phone and the tablet the host's Kick was squeezed on, and the narrowest at a large font. */
        val DIALOG_SIZES = listOf(393 to 1f, TABLET to 1f, WIDTH to LARGE_FONT)
    }
}
