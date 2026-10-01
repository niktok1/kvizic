package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.protocol.CloseCodes
import io.ntole.kvizic.core.protocol.CloseReason
import io.ntole.kvizic.core.protocol.LeaveReason
import io.ntole.kvizic.core.protocol.MemberView
import io.ntole.kvizic.core.protocol.RejectCode
import io.ntole.kvizic.core.protocol.ServerMessage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Members voting one of their own out of a waiting room, as the lobby's loop counts the votes: more than
 * half of the others in the room, and never fewer than two; one vote each at a time, a new one at most once
 * per [GameTimings.kickVoteEvery]; and the room told each tally as it moves.
 */
class LobbyVoteKickTest {
    private fun LobbyScenario.join(vararg names: String): List<TestPlayer> = names.map { player(it).join() }

    /** [member] as this player was last told of them. */
    private fun TestPlayer.sees(member: TestPlayer): MemberView =
        history
            .mapNotNull { message ->
                when (message) {
                    is ServerMessage.Snapshot -> message.lobby.members.firstOrNull { it.player == member.id }
                    is ServerMessage.MemberJoined -> message.member.takeIf { it.player == member.id }
                    is ServerMessage.MemberUpdated -> message.member.takeIf { it.player == member.id }
                    else -> null
                }
            }.last()

    /** The votes against a member, and how many it takes. */
    private fun MemberView.tally(): Pair<Int, Int> = kickVotes to kickVotesNeeded

    @Test
    fun `more than half of the others in the room vote a member out, and the room will not take them back`() =
        runTest {
            val lobby = LobbyScenario(this)
            val (ana, boris, ceca, dora) = lobby.join("ana", "boris", "ceca", "dora")

            assertNull(boris.answerTo(boris.voteKick(dora)))
            // Ana, boris and ceca may vote dora out: it takes two.
            assertEquals(1 to 2, ana.sees(dora).tally())
            assertEquals(1 to 2, dora.sees(dora).tally(), "dora sees it coming")
            assertTrue(boris.sees(dora).kickVoted, "boris is told his own vote")
            assertFalse(ceca.sees(dora).kickVoted, "and nobody is told another's")

            assertNull(ceca.answerTo(ceca.voteKick(dora)))
            assertEquals(CloseCodes.KICKED, dora.closedCode())
            assertEquals(ServerMessage.Closing(CloseReason.VOTED_OUT), dora.history.last())
            assertEquals(LeaveReason.VOTED_OUT, ana.last<ServerMessage.MemberLeft>().reason)
            assertEquals(ReserveResult.Banned, dora.reserve())
            lobby.assertInvariants()
        }

    @Test
    fun `the host can be voted out too, and hosting passes to whoever has been there longest`() =
        runTest {
            val lobby = LobbyScenario(this)
            val (ana, boris, ceca) = lobby.join("ana", "boris", "ceca")

            boris.voteKick(ana)
            ceca.voteKick(ana)
            assertEquals(CloseCodes.KICKED, ana.closedCode())
            assertEquals("boris", ceca.last<ServerMessage.HostChanged>().host)
            lobby.assertInvariants()
        }

    @Test
    fun `of two players neither can vote the other out, and a solo run takes no vote`() =
        runTest {
            val lobby = LobbyScenario(this)
            val (ana, boris) = lobby.join("ana", "boris")

            assertNull(boris.answerTo(boris.voteKick(ana)))
            assertEquals(1 to 2, ana.sees(ana).tally(), "boris alone could vote: it would take two")
            lobby.wait(1.minutes)
            assertNull(ana.closedCode())

            val solo = LobbyScenario(this, kind = LobbyKind.SOLO)
            val runner = solo.player("ana").join()
            assertEquals(RejectCode.WRONG_PHASE, runner.answerTo(runner.voteKick(null)))
        }

    @Test
    fun `a vote counts while its voter is in the room, and what it takes moves with who is`() =
        runTest {
            val lobby = LobbyScenario(this)
            val (ana, boris, ceca, dora, eva) = lobby.join("ana", "boris", "ceca", "dora", "eva")

            boris.voteKick(eva)
            ceca.voteKick(eva)
            // Ana, boris, ceca and dora may vote: it takes three.
            assertEquals(2 to 3, ana.sees(eva).tally())

            ceca.drop()
            assertEquals(1 to 2, ana.sees(eva).tally(), "ceca's vote counts only while she is there")
            ceca.attach()
            assertEquals(2 to 3, ana.sees(eva).tally(), "and again once she is back")

            dora.leave()
            // Of ana, boris and ceca, two are more than half.
            assertEquals(CloseCodes.KICKED, eva.closedCode())
            assertEquals(LeaveReason.VOTED_OUT, ana.last<ServerMessage.MemberLeft>().reason)
            lobby.assertInvariants()
        }

    @Test
    fun `a member still on the results neither votes nor counts toward what it takes`() =
        runTest {
            val lobby = LobbyScenario(this)
            val (ana, boris, ceca, dora) = lobby.join("ana", "boris", "ceca", "dora")
            ana.start()
            // Nobody answers, and the game plays itself out to its results.
            lobby.wait(lobby.timings.countdown + 3.minutes)
            assertTrue(ana.all<ServerMessage.GameOver>().isNotEmpty())

            ana.back()
            boris.back()
            assertEquals(RejectCode.WRONG_PHASE, ceca.answerTo(ceca.voteKick(dora)), "ceca is still on the results")
            assertNull(boris.answerTo(boris.voteKick(dora)))
            // Ana and boris are back, ceca is not: it takes both of them.
            assertEquals(1 to 2, ana.sees(dora).tally())
            ana.voteKick(dora)
            assertEquals(CloseCodes.KICKED, dora.closedCode())
            lobby.assertInvariants()
        }

    @Test
    fun `one vote at a time, a new one at most every half minute, and taking one back is never held up`() =
        runTest {
            val lobby = LobbyScenario(this)
            val (ana, boris, _, dora, eva) = lobby.join("ana", "boris", "ceca", "dora", "eva")

            assertNull(boris.answerTo(boris.voteKick(dora)))
            assertEquals(RejectCode.TOO_SOON, boris.answerTo(boris.voteKick(eva)), "no moving it at once")
            assertNull(boris.answerTo(boris.voteKick(dora)), "the same vote again changes nothing")
            assertEquals(1 to 3, ana.sees(dora).tally())
            assertNull(boris.answerTo(boris.voteKick(null)), "taking it back is never held up")
            assertEquals(0 to 0, ana.sees(dora).tally())
            assertEquals(RejectCode.TOO_SOON, boris.answerTo(boris.voteKick(dora)), "nor casting it again")
            assertEquals(RejectCode.NO_SUCH_PLAYER, boris.answerTo(boris.voteKick(boris)), "not himself")

            lobby.wait(lobby.timings.kickVoteEvery)
            assertNull(boris.answerTo(boris.voteKick(eva)))
            assertEquals(1 to 3, ana.sees(eva).tally())
            lobby.assertInvariants()
        }

    @Test
    fun `a vote that put someone out leaves its voters free to vote again at once`() =
        runTest {
            val lobby = LobbyScenario(this)
            val (ana, boris, ceca, dora, eva) = lobby.join("ana", "boris", "ceca", "dora", "eva")

            listOf(boris, ceca, dora).forEach { it.voteKick(eva) }
            assertEquals(CloseCodes.KICKED, eva.closedCode())
            assertNull(boris.answerTo(boris.voteKick(dora)))
            assertEquals(1 to 2, ana.sees(dora).tally())
        }

    @Test
    fun `a game wipes the votes, and none are taken while one is on`() =
        runTest {
            val lobby = LobbyScenario(this)
            val (ana, boris, ceca, dora) = lobby.join("ana", "boris", "ceca", "dora")

            boris.voteKick(dora)
            ana.start()
            assertEquals(0 to 0, ceca.sees(dora).tally())
            assertFalse(boris.sees(dora).kickVoted)
            lobby.wait(lobby.timings.countdown)
            assertEquals(RejectCode.WRONG_PHASE, ceca.answerTo(ceca.voteKick(dora)))
            lobby.assertInvariants()
        }
}
