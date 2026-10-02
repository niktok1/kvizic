package io.ntole.kvizic.design.sound

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A moment the game answers with a sound. A cue names the moment, never the sound: each skin's
 * [SkinSound] has its own sample for every one, so a skin swaps the whole voice of the game and a
 * screen asks only for what happened.
 *
 * [minGapMillis] is the least time between two plays of the cue, the same cue asked again sooner is
 * dropped (a crowd of reactions, eight players locking in, a keypad hammered), and [varied] cues are
 * played a hair off pitch each time, so a run of them never sounds like a machine.
 */
enum class Cue(
    val minGapMillis: Int = 0,
    val varied: Boolean = false,
) {
    // The chrome: what a finger on a control says.

    /** The one thing a screen is for: a hero button. */
    TAP_PRIMARY(minGapMillis = 60),

    /** Another way on: a regular button, a plate. */
    TAP(minGapMillis = 60),

    /** The smallest of choices: a quiet button, a chip, an icon, a seat. */
    TAP_SOFT(minGapMillis = 40, varied = true),

    /** A way back. */
    BACK(minGapMillis = 80),

    /** A switch turned on, and off. */
    TOGGLE_ON(minGapMillis = 80),
    TOGGLE_OFF(minGapMillis = 80),

    /** A dialog coming up, and going away. */
    DIALOG_OPEN(minGapMillis = 120),
    DIALOG_CLOSE(minGapMillis = 120),

    /** A key of the join keypad, and its delete. */
    KEY(minGapMillis = 30, varied = true),
    KEY_DELETE(minGapMillis = 30),

    /** Something refused: a code that is not a room's, a command the room would not take. */
    ERROR(minGapMillis = 400),

    /** A room's code copied. */
    COPIED(minGapMillis = 400),

    // The lobby: who comes and goes, and what the room says.

    /** The player has a seat in a room. */
    WELCOME,

    /** Another player sits down, and gets up. */
    JOINED(minGapMillis = 250),
    LEFT(minGapMillis = 250),

    /** The room is the player's to run: they made it, or its host handed it over. */
    HOST,

    /** The player is put out of the room, by the host or by a vote. */
    KICKED,

    /** A vote to put someone out, anyone's. */
    VOTE(minGapMillis = 300),

    /** A line the room says, softly: the server restarting, the game shortened. */
    NOTICE(minGapMillis = 500),

    /** The connection to the room lost, and made again. */
    LINK_LOST(minGapMillis = 2_000),
    LINK_BACK(minGapMillis = 2_000),

    /** The reactions, the server's seven. */
    REACT_BRAVO(minGapMillis = 150),
    REACT_CLAP(minGapMillis = 150),
    REACT_FIRE(minGapMillis = 150),
    REACT_WOW(minGapMillis = 150),
    REACT_LAUGH(minGapMillis = 150),
    REACT_OOPS(minGapMillis = 150),

    /** The nudge: a bell, for the host. */
    NUDGE(minGapMillis = 800),

    // A game.

    /** One of the seconds before a game, and the game starting. */
    COUNT(minGapMillis = 200),
    GO,

    /** A question coming on, and each of its answers' tiles after it. */
    QUESTION,
    TILE(minGapMillis = 60, varied = true),

    /** The player's answer locked in, and everyone's. */
    LOCK,
    ALL_IN,

    /** One of the last seconds of a question, and the time being up. */
    TICK(minGapMillis = 400),
    TIME_UP,

    /** How the player's answer went: right, wrong, or none given. */
    RIGHT,
    WRONG,
    MISSED,

    /** One of the first three right answers, with its bonus. */
    BONUS,

    /** The player's place on the board improving, or slipping. */
    RANK_UP(minGapMillis = 600),
    RANK_DOWN(minGapMillis = 600),

    /** The last question of a game coming on. */
    LAST,

    // A game's end.

    /** The player won, was on the podium, or only played to the end. */
    WIN,
    PODIUM,
    END,

    /** A solo run beating the player's best. */
    BEST,
    ;

    /** The name of the cue's sample in a skin's bank: `files/sound/<bank>/<file>.wav`. */
    val file: String get() = name.lowercase()
}

/**
 * What components and screens ask to make a sound. The app provides the one that plays ([LocalCues]); a
 * screen drawn on its own, as a test draws one, has [None], which is silent.
 */
interface Cues {
    /** Plays [cue], [volume] of its loudness (0 to 1); a cue asked while sound is off or not ready is dropped. */
    fun play(
        cue: Cue,
        volume: Float = 1f,
    )

    /** Makes no sound. */
    object None : Cues {
        override fun play(
            cue: Cue,
            volume: Float,
        ) = Unit
    }
}

/** The app's [Cues], bound to the skin worn. */
val LocalCues = staticCompositionLocalOf<Cues> { Cues.None }
