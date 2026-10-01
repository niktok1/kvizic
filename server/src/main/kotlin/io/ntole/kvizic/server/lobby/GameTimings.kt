package io.ntole.kvizic.server.lobby

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How long each step of a lobby's life takes. Server data, so tuning one needs no client release; the
 * tests run on [FAST].
 */
data class GameTimings(
    /** From the host's start to the first question: long enough for the room to settle in. */
    val countdown: Duration = 5.seconds,
    /** The same, when a member still looks at the last game's results, so they can tap in. */
    val countdownWithStragglers: Duration = 7.seconds,
    /**
     * How long a question shows alone before its answers: a base, plus a reader's time per character, up to
     * a cap. The longest question the rules allow, 120 characters, reads in 6.9 seconds, one of 40 in 3.3.
     */
    val readBase: Duration = 1_500.milliseconds,
    val readPerCharacter: Duration = 45.milliseconds,
    val readMax: Duration = 7.seconds,
    /** How long the right answer and the points show. */
    val reveal: Duration = 5.seconds,
    /** The same, for a question with an explanation to read. */
    val revealWithExplanation: Duration = 7.seconds,
    /** How late an answer may land after the clock ran out and still count, at the full time. */
    val answerGrace: Duration = 500.milliseconds,
    /** The most of a connection's measured round trip taken off its answers' times. */
    val rttCap: Duration = 300.milliseconds,
    /** How long a start waits for its questions past the countdown before giving up. */
    val questionLoadTimeout: Duration = 5.seconds,
    /** How long a member who dropped out of a waiting lobby keeps their seat. In a game, it is the whole game. */
    val lobbyGrace: Duration = 2.minutes,
    /** How long a socket ticket works, and so how long a seat is held for the socket to come. */
    val ticketTtl: Duration = 30.seconds,
    /** A waiting lobby with no game started this long closes. */
    val idle: Duration = 30.minutes,
    /** No lobby lives longer. */
    val maxLifetime: Duration = 12.hours,
    /** A public lobby's host who does nothing this long, with others waiting, hands hosting over. */
    val afkHost: Duration = 3.minutes,
    /** A public lobby's host gone this long hands hosting over; a private host keeps it through their grace. */
    val absentPublicHost: Duration = 30.seconds,
    /**
     * How long a player whose socket just closed is still waited for before a question reveals early: a
     * phone that loses its network for a moment comes back with the answer it tapped. The question's own
     * clock still bounds it.
     */
    val dropGrace: Duration = 3.seconds,
    /** How often a lobby checks its deadlines. */
    val tick: Duration = 1.seconds,
    /** How long a socket has to send its hello. */
    val helloTimeout: Duration = 5.seconds,
    /** How often the server pings a socket, which also keeps idle proxies from closing it. */
    val pingEvery: Duration = 5.seconds,
    /** A socket this long without a frame is dead. */
    val silentAfter: Duration = 20.seconds,
    /** How long a waiting lobby lives into a drain before it closes. */
    val drainWaitingLobby: Duration = 5.seconds,
    /** The fewest time between two reactions of one member, after a burst of [reactionBurst]. */
    val reactionEvery: Duration = 1_500.milliseconds,
    val reactionBurst: Int = 3,
    /**
     * The fewest time between two votes one member casts to put someone out, so nobody spams them; taking
     * a vote back is never held up.
     */
    val kickVoteEvery: Duration = 30.seconds,
) {
    /** How long [text] shows before its answers. */
    fun readTime(text: String): Duration = (readBase + readPerCharacter * text.length).coerceAtMost(readMax)

    companion object {
        val DEFAULT: GameTimings = GameTimings()

        /** Everything short, for tests on real time, which can't skip ahead. */
        val FAST: GameTimings =
            GameTimings(
                countdown = 50.milliseconds,
                countdownWithStragglers = 100.milliseconds,
                readBase = 50.milliseconds,
                readPerCharacter = Duration.ZERO,
                readMax = 50.milliseconds,
                reveal = 100.milliseconds,
                revealWithExplanation = 150.milliseconds,
                answerGrace = 100.milliseconds,
                questionLoadTimeout = 2.seconds,
                lobbyGrace = 2.seconds,
                dropGrace = 2.seconds,
                ticketTtl = 5.seconds,
                tick = 50.milliseconds,
                // Generous, so a loaded machine never trips them: their own tests set them short.
                helloTimeout = 5.seconds,
                pingEvery = 200.milliseconds,
                silentAfter = 10.seconds,
                drainWaitingLobby = 100.milliseconds,
                reactionEvery = 50.milliseconds,
            )
    }
}

/** How much the server holds at once, and how much one socket may send. */
data class LobbyLimits(
    val maxLobbies: Int = 500,
    /**
     * Sockets one client address may hold at once, `MAX_SOCKETS_PER_ADDRESS`. Generous: a mobile carrier puts
     * many phones behind one address (CGNAT), and every socket needs a seat its session took over REST, whose
     * guests are minted at a budget per address, so this bounds little an abuser could not do anyway.
     */
    val maxSocketsPerAddress: Int = 200,
    /** The longest frame a client may send. The largest correct one, a settings change, is far smaller. */
    val maxClientFrameBytes: Int = 4_096,
    /** A socket's frame budget: this many a second on average, with bursts up to [frameBurst]. */
    val framesPerSecond: Int = 10,
    val frameBurst: Int = 20,
    /** How many frames the server queues for one slow socket before closing it. */
    val outgoingQueue: Int = 64,
) {
    companion object {
        val DEFAULT: LobbyLimits = LobbyLimits()
    }
}
