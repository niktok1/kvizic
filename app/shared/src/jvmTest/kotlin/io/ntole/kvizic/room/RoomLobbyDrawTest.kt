package io.ntole.kvizic.room

import androidx.compose.ui.ImageComposeScene
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbyKind
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.nodes
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The lobby drawn off screen in every skin: waiting, for a member and for its host, a vote to put a player
 * out, the countdown, and a dropped connection.
 */
class RoomLobbyDrawTest : RoomStills() {
    @Test
    fun `the lobby shows its code in the bar, its seats, its settings and the reactions, and counts in no words`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            val copied = mutableListOf<String>()
            draw(skin, "lobby", inLobby(GamePhase.Waiting(null)), RoomActions(copyCode = { copied += it })) { scene ->
                val shown = scene.everyText()
                val said = scene.descriptions()
                val code = words.privateRoom + ", " + words.roomCode + ": 4 8 2 9 1 5"
                assertTrue(code in said, "${skin.id}: the code is not said as $code: $said")
                // The seats show who is in and how many more fit, the host's microphone and the player's own.
                listOf("5 / 8", words.players, words.freeSeat, words.host, words.you).forEach {
                    assertFalse(it in shown, "${skin.id}: \"$it\" is written in $shown")
                }
                assertEquals(3, said.count { it == words.freeSeat }, "${skin.id}: $said")
                listOf(words.host, words.you).forEach { word ->
                    assertTrue(word in said, "${skin.id}: no seat says \"$word\": $said")
                }
                EMOTES.forEach { assertTrue(it.name(words) in said, "${skin.id}: ${it.id}") }
                // The code stands in the top bar, over the seats, never on them.
                val bar = scene.nodes().first { code in it.descriptions }
                val seat = scene.nodes().first { "Нина" in it.texts }.boundsInRoot
                assertTrue(bar.boundsInRoot.bottom <= seat.top, "${skin.id}: the code at $bar is on the seats at $seat")
                assertTrue(words.reactionNudge in shown, "${skin.id}: a member cannot nudge the host")
                assertFalse(words.start in shown, "${skin.id}: a member has no Start")
                // A long press copies the code.
                bar.config[androidx.compose.ui.semantics.SemanticsActions.OnLongClick].action?.invoke()
            }
            assertEquals(listOf("482915"), copied, skin.id)
        }
    }

    @Test
    fun `a public room's code is said as public`() {
        val words = stringsOf(Language.DEFAULT).game
        val state = inLobby(GamePhase.Waiting(null), lobby = lobby(kind = LobbyKind.PUBLIC))
        draw(Skins.Default, "lobby-public", state) { scene ->
            val code = words.publicRoom + ", " + words.roomCode + ": 4 8 2 9 1 5"
            assertTrue(code in scene.descriptions(), "${scene.descriptions()}")
        }
    }

    @Test
    fun `the host starts the game, and taps a member to choose what to do with them`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            var started = 0
            val kicked = mutableListOf<String>()
            val state = inLobby(GamePhase.Waiting(null), lobby = lobby(host = YOU))
            draw(skin, "lobby-host", state, RoomActions(start = { started++ }, kick = { kicked += it })) { scene ->
                assertTrue(words.start in scene.texts(), "${skin.id}: ${scene.texts()}")
                assertFalse(words.reactionNudge in scene.texts(), "${skin.id}: the host can nudge themselves")
                scene.tap(words.start)
                val nina = scene.nodes().first { "Нина" in it.texts }
                nina.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action?.invoke()
                scene.renderSettled()
                scene.tap(words.removePlayer)
            }
            assertEquals(1, started, skin.id)
            assertEquals(listOf("nina"), kicked, skin.id)
        }
    }

    @Test
    fun `a member votes a player out from their seat, sees the count there, and takes the vote back`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            val votes = mutableListOf<String?>()
            draw(
                skin,
                "lobby-vote",
                inLobby(GamePhase.Waiting(null)),
                RoomActions(voteKick = { votes += it }),
            ) { scene ->
                tapSeat(scene, "Бојан")
                assertFalse(words.withdrawVote in scene.texts(), "${skin.id}: no vote of his to take back")
                scene.tap(words.voteKick)
            }
            val voted =
                MEMBERS.map {
                    if (it.playerId ==
                        "bojan"
                    ) {
                        it.copy(kickVotes = 2, kickVotesNeeded = 3, kickVotedByYou = true)
                    } else {
                        it
                    }
                }
            val state = inLobby(GamePhase.Waiting(null), lobby = lobby(members = voted))
            draw(skin, "lobby-voted", state, RoomActions(voteKick = { votes += it })) { scene ->
                val said = words.kickVotes.fill(2, 3)
                assertTrue(said in scene.descriptions(), "${skin.id}: the seat does not say the votes")
                tapSeat(scene, "Бојан")
                assertTrue(said in scene.everyText(), "${skin.id}: the dialog does not say the votes")
                scene.tap(words.withdrawVote)
            }
            assertEquals(listOf("bojan", null), votes, skin.id)
        }
    }

    @Test
    fun `nobody votes during the countdown, but the host still chooses`() {
        val words = stringsOf(Language.DEFAULT).game
        draw(Skins.Default, "countdown-member", inLobby(GamePhase.Countdown(deadline(3.seconds()), null))) { scene ->
            val seat = scene.nodes().first { "Бојан" in it.texts }
            assertFalse(androidx.compose.ui.semantics.SemanticsActions.OnClick in seat.config, "a seat to vote from")
            assertFalse(words.voteKick in scene.texts())
        }
    }

    @Test
    fun `the countdown takes the start's place`() {
        eachSkin { skin ->
            val words = stringsOf(Language.DEFAULT).game
            draw(
                skin,
                "countdown",
                inLobby(GamePhase.Countdown(deadline(3.seconds()), null), lobby = lobby(host = YOU)),
            ) { scene ->
                assertTrue(words.gameStarting in scene.everyText(), "${skin.id}: ${scene.everyText()}")
                assertFalse(words.start in scene.everyText(), skin.id)
            }
        }
    }

    @Test
    fun `a dropped connection says it is being made again`() {
        val words = stringsOf(Language.DEFAULT).game
        draw(Skins.Default, "reconnecting", inLobby(GamePhase.Waiting(null), reconnecting = true)) { scene ->
            assertTrue(words.reconnecting in scene.everyText(), "${scene.everyText()}")
        }
    }

    /** Taps the seat of the member named [name], as a finger would. */
    private fun tapSeat(
        scene: ImageComposeScene,
        name: String,
    ) {
        val seat = scene.nodes().first { name in it.texts }
        seat.config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action?.invoke()
        scene.renderSettled()
    }
}
