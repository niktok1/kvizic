package io.ntole.kvizic.room

import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.GameResults
import io.ntole.kvizic.core.domain.lobby.LobbyKind
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.PersonalBest
import io.ntole.kvizic.design.sound.Cue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What the room sounds like, as its state changes: the policy without a scene or a speaker. */
class RoomCueTrackerTest {
    private val tracker = RoomCueTracker(tileStaggerMillis = 70)

    /** The cues the room changing to [next] calls for, after being [first]. */
    private fun cues(
        first: LobbySessionState.InLobby,
        next: LobbySessionState.InLobby,
    ): List<Cue> {
        tracker.heard(first)
        return tracker.heard(next).map { it.cue }
    }

    private val waiting = inLobby(GamePhase.Waiting(null))

    @Test
    fun `the first state is how things stand and sounds nothing`() {
        assertTrue(tracker.heard(inLobby(answering())).isEmpty())
    }

    @Test
    fun `another lobby is how things stand too`() {
        tracker.heard(waiting)
        val other = waiting.copy(lobby = lobby().copy(id = "lobby-2", members = MEMBERS.dropLast(1)))
        assertTrue(tracker.heard(other).isEmpty())
    }

    @Test
    fun `someone coming and going is heard softly and the player's own seat never is`() {
        val fewer = waiting.copy(lobby = lobby(members = MEMBERS.filter { it.playerId != "bojan" }))
        tracker.heard(waiting)
        assertEquals(listOf(Heard(Cue.LEFT, volume = 0.6f)), tracker.heard(fewer))
        assertEquals(listOf(Heard(Cue.JOINED, volume = 0.6f)), tracker.heard(waiting))
        val withoutYou = waiting.copy(lobby = lobby(members = MEMBERS.filter { it.playerId != YOU }))
        assertTrue(tracker.heard(withoutYou).isEmpty())
    }

    @Test
    fun `the room handed to the player is heard and handed to another is not`() {
        val mine = waiting.copy(lobby = lobby(host = YOU))
        assertEquals(listOf(Cue.HOST), cues(waiting, mine))
        assertTrue(cues(mine, waiting).isEmpty())
        val other = waiting.copy(lobby = lobby(host = "sova"))
        assertTrue(cues(waiting, other).isEmpty())
    }

    @Test
    fun `a vote put in is heard and one taken back is not`() {
        val voted = MEMBERS.map { if (it.playerId == "bojan") it.copy(kickVotes = 1, kickVotesNeeded = 3) else it }
        val withVote = waiting.copy(lobby = lobby(members = voted))
        assertEquals(listOf(Cue.VOTE), cues(waiting, withVote))
        assertTrue(cues(withVote, waiting).isEmpty())
    }

    @Test
    fun `the connection lost and made again is heard`() {
        val lost = waiting.copy(reconnecting = true)
        assertEquals(listOf(Cue.LINK_LOST), cues(waiting, lost))
        assertEquals(listOf(Cue.LINK_BACK), cues(lost, waiting))
        assertTrue(cues(waiting, waiting).isEmpty())
    }

    @Test
    fun `a game starts with a go and each question comes on with a sound and the last with a stinger`() {
        val countdown = inLobby(GamePhase.Countdown(deadline(kotlin.time.Duration.parse("5s")), null))
        val first = inLobby(reading().copy(question = QUESTION.copy(index = 0)))
        assertEquals(listOf(Cue.GO), cues(countdown, first))
        val middle = inLobby(reading())
        assertEquals(listOf(Cue.QUESTION), cues(inLobby(revealing()), middle))
        val last = inLobby(reading().copy(question = QUESTION.copy(index = 9)))
        assertEquals(listOf(Cue.LAST), cues(inLobby(revealing()), last))
        assertTrue(cues(middle, middle).isEmpty(), "the same question again is not a new one")
    }

    @Test
    fun `the answers opening sound each tile as its tile comes on`() {
        val reading = inLobby(reading())
        val answering = inLobby(answering())

        tracker.heard(reading)
        val heard = tracker.heard(answering)

        assertEquals(List(OPTIONS.size) { Cue.TILE }, heard.map { it.cue })
        assertEquals(listOf(0L, 70L, 140L, 210L), heard.map { it.afterMillis })
        assertTrue(cues(answering, inLobby(answering(answered = setOf("sova")))).isEmpty(), "answers coming in")
    }

    @Test
    fun `a game's end sounds for how the player did`() {
        val me = MEMBERS.map { if (it.playerId == YOU) it.copy(onResults = true) else it }
        val revealing = inLobby(revealing(last = true), lobby = lobby(members = me))

        fun end(results: GameResults) = cues(revealing, revealing.copy(phase = GamePhase.Waiting(results)))

        assertEquals(listOf(Cue.PODIUM), end(RESULTS))
        val winner =
            RESULTS.copy(
                standings =
                    RESULTS.standings.map {
                        it.copy(
                            rank =
                                if (it.playerId ==
                                    YOU
                                ) {
                                    1
                                } else {
                                    it.rank + 1
                                },
                        )
                    },
            )
        assertEquals(listOf(Cue.WIN), end(winner))
        val fourth =
            RESULTS.copy(
                standings =
                    RESULTS.standings.map {
                        it.copy(
                            rank =
                                if (it.playerId ==
                                    YOU
                                ) {
                                    4
                                } else {
                                    2
                                },
                        )
                    },
            )
        assertEquals(listOf(Cue.END), end(fourth))
    }

    @Test
    fun `a solo run sounds its best only when it is one`() {
        val me = MEMBERS.filter { it.playerId == YOU }.map { it.copy(onResults = true) }
        val solo = lobby(members = me, kind = LobbyKind.SOLO, host = YOU)
        val revealing = inLobby(revealing(last = true), lobby = solo)

        fun end(best: PersonalBest) =
            cues(
                revealing,
                revealing.copy(
                    phase = GamePhase.Waiting(RESULTS.copy(standings = RESULTS.standings.take(2), personalBest = best)),
                ),
            )

        assertEquals(listOf(Cue.BEST), end(PersonalBest(score = 1488, previous = 1200, isNew = true)))
        assertEquals(listOf(Cue.END), end(PersonalBest(score = 1100, previous = 1200, isNew = false)))
    }

    @Test
    fun `results already shown and a player not on them sound nothing`() {
        val me = MEMBERS.map { if (it.playerId == YOU) it.copy(onResults = true) else it }
        val onResults = inLobby(GamePhase.Waiting(RESULTS), lobby = lobby(members = me))
        assertTrue(cues(onResults, onResults).isEmpty(), "the same results again")
        val elsewhere = inLobby(GamePhase.Waiting(RESULTS))
        assertTrue(cues(inLobby(revealing(last = true)), elsewhere).isEmpty(), "back in the room already")
    }

    @Test
    fun `every reaction the server has a sound of its own and an unknown one none`() {
        val sounds =
            io.ntole.kvizic.core.domain.lobby.LobbyRules.REACTIONS
                .map { reactionCue(it) }
        assertTrue(sounds.all { it != null }, "$sounds")
        assertEquals(sounds.size, sounds.toSet().size, "each its own")
        assertEquals(null, reactionCue("confetti"))
    }

    @Test
    fun `a refusal is an error and a notice a ping and a copied code is sounded where it is copied`() {
        listOf(RoomNote.OnlyHost, RoomNote.Refused, RoomNote.VoteTooSoon, RoomNote.ReportFailed).forEach {
            assertEquals(Cue.ERROR, noteCue(it), "$it")
        }
        listOf(
            RoomNote.ServerRestarting,
            RoomNote.TopicsToppedUp,
            RoomNote.GameShortened,
            RoomNote.Reported,
            RoomNote.HostIdle,
            RoomNote.RoomIdle,
            RoomNote.SettingsChanged(LobbySettings(), LobbySettings(questionCount = 20)),
            RoomNote.HostChanged("Нина", you = false),
        ).forEach { assertEquals(Cue.NOTICE, noteCue(it), "$it") }
        assertEquals(null, noteCue(RoomNote.CodeCopied))
        assertEquals(null, noteCue(RoomNote.HostChanged("Марко", you = true)), "the host's own cue is the tracker's")
    }
}
