package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.Reactions
import io.ntole.kvizic.core.protocol.ServerMessage
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Random sequences of what players do, joins, drops, returns, kicks, starts, answers and waits, thrown at
 * one lobby, then checked against what must always hold: seats never past the limit, one host who is a
 * member, every socket's versions gapless, every final score the sum of that player's revealed points,
 * and a closed lobby empty.
 */
class LobbyFuzzTest {
    @Test
    fun `whatever players do, the lobby's invariants hold`() {
        (1..SEEDS).forEach { seed -> runSeed(seed) }
        // The runs must reach real games, or the invariants hold only over nothing.
        assertTrue(gamesPlayed >= SEEDS, "only $gamesPlayed games over $SEEDS seeds")
        assertTrue(answersRevealed >= SEEDS * 5, "only $answersRevealed answers revealed")
    }

    private var gamesPlayed = 0
    private var answersRevealed = 0

    private fun runSeed(seed: Int) =
        runTest {
            val random = Random(seed)
            val lobby =
                LobbyScenario(
                    this,
                    LobbySettingsDto(questionCount = 5, secondsPerQuestion = 10, maxPlayers = 6),
                    seed = seed,
                )
            // Joins first and never leaves or drops, so it sees every message of every game.
            val watcher = lobby.player("watcher").join()
            val others = (1..8).map { lobby.player("p$it") }

            repeat(STEPS) {
                if (lobby.closed) return@repeat
                val player = others.random(random)
                when (random.nextInt(17)) {
                    0 -> {
                        if (player.connection ==
                            null
                        ) {
                            player.reserve().also { if (it == ReserveResult.Reserved) player.attach() }
                        }
                    }

                    1 -> {
                        if (player.connection != null) player.drop()
                    }

                    2 -> {
                        if (player.connection == null && isMember(lobby, watcher, player)) player.attach()
                    }

                    3 -> {
                        if (player.connection != null && random.nextInt(4) == 0) player.leave()
                    }

                    4 -> {
                        hostOf(lobby, watcher, others)?.let { host ->
                            if (host.connection !=
                                null
                            ) {
                                host.kick(others.random(random))
                            }
                        }
                    }

                    5 -> {
                        hostOf(lobby, watcher, others)?.let { host -> if (host.connection != null) host.start() }
                    }

                    6, 7, 8, 9 -> {
                        (others + watcher)
                            .filter {
                                it.connection != null
                            }.forEach {
                                if (random.nextBoolean()) {
                                    answerSomething(
                                        it,
                                        random,
                                    )
                                }
                            }
                    }

                    10 -> {
                        if (player.connection != null) player.back()
                    }

                    11 -> {
                        if (player.connection != null) player.say(ClientMessage.React(Reactions.ALL.random(random)))
                    }

                    12 -> {
                        watcher.back().also { if (random.nextBoolean()) watcher.start() }
                    }

                    13 -> {
                        lobby.wait(random.nextLong(5_000, 30_000).milliseconds)
                    }

                    14 -> {
                        // A vote against another, never the watcher, who must see everything; or one taken back.
                        val against = others.random(random).takeIf { random.nextInt(4) != 0 }
                        if (player.connection != null) player.voteKick(against)
                    }

                    else -> {
                        lobby.wait(random.nextLong(50, 3_000).milliseconds)
                    }
                }
                assertTrue(lobby.summary().seatsTaken <= 6, "seed $seed: seats past the limit")
            }
            // Let whatever game is running finish.
            lobby.wait(lobby.timings.maxLifetime / 100)

            lobby.assertInvariants()
            assertScoresAreTheirReveals(watcher, seed)
            if (!lobby.closed) {
                val snapshot = watcher.snapshotNow(lobby)
                assertTrue(
                    snapshot.lobby.members.any { it.player == snapshot.lobby.host },
                    "seed $seed: the host is a member",
                )
            }
        }

    private fun answerSomething(
        player: TestPlayer,
        random: Random,
    ) {
        val opened =
            player.history.lastOrNull { it is ServerMessage.AnswersOpened } as? ServerMessage.AnswersOpened ?: return
        player.answer(random.nextInt(-1, opened.options.size + 1), question = opened.index)
    }

    /** Every final score equals the sum of the points that player's reveals showed, game by game. */
    private fun assertScoresAreTheirReveals(
        watcher: TestPlayer,
        seed: Int,
    ) {
        var sums = mutableMapOf<String, Int>()
        var games = 0
        watcher.history.forEach { message ->
            when (message) {
                is ServerMessage.GameStarted -> {
                    sums = message.players.associateWith { 0 }.toMutableMap()
                }

                is ServerMessage.Revealed -> {
                    answersRevealed += message.reveal.results.count { it.option != null }
                    message.reveal.results.forEach { sums.merge(it.player, it.points, Int::plus) }
                }

                is ServerMessage.GameOver -> {
                    games++
                    gamesPlayed++
                    message.results.standings.forEach { standing ->
                        assertEquals(
                            sums[standing.player] ?: 0,
                            standing.score,
                            "seed $seed: ${standing.player}'s score",
                        )
                    }
                }

                else -> {
                    Unit
                }
            }
        }
        assertTrue(games >= 0)
    }

    private fun isMember(
        lobby: LobbyScenario,
        watcher: TestPlayer,
        player: TestPlayer,
    ): Boolean =
        watcher
            .snapshotNow(lobby)
            .lobby.members
            .any { it.player == player.id } ||
            player.id in lobby.summary().connected

    private fun hostOf(
        lobby: LobbyScenario,
        watcher: TestPlayer,
        others: List<TestPlayer>,
    ): TestPlayer? {
        val host = watcher.snapshotNow(lobby).lobby.host
        return if (host == watcher.id) watcher else others.firstOrNull { it.id == host }
    }

    private fun TestPlayer.snapshotNow(lobby: LobbyScenario): ServerMessage.Snapshot {
        say(ClientMessage.Resync)
        lobby.settle()
        return checkNotNull(lastSnapshot())
    }

    private companion object {
        const val SEEDS = 40
        const val STEPS = 250
    }
}
