package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Every colour a skin paints with, by the role it plays, never by its hue: a skin may make the page
 * paper and the plates black. Each `on` colour is the text and icons drawn on the colour it is named
 * for, and `SkinContrastTest` holds every such pair to WCAG AA in every skin.
 *
 * A `side` is the part of a raised thing that shows below its face: an extrusion, or a shadow.
 */
@Immutable
data class SkinColors(
    /** The page every screen stands on, under the backdrop's art. */
    val page: Color,
    val onPage: Color,
    val onPageMuted: Color,
    /** A word on the page that stands out: a label's emphasis, the round's number. */
    val onPageAccent: Color,
    /** A dark raised surface: a panel, a seat, the board a code is shown on. */
    val raised: Color,
    val raisedSide: Color,
    val onRaised: Color,
    val onRaisedMuted: Color,
    /** The line drawn round every raised thing. */
    val outline: Color,
    /** A light raised plate: an answer tile at rest, a secondary button. */
    val plate: Color,
    val plateSide: Color,
    val onPlate: Color,
    val onPlateMuted: Color,
    /** The main action, and the colour a player's own choice glows in. */
    val primary: Color,
    val primarySide: Color,
    val onPrimary: Color,
    /** A tile the player locked their answer in on. */
    val lockedIn: Color,
    val lockedInSide: Color,
    val onLockedIn: Color,
    val correct: Color,
    val correctSide: Color,
    val onCorrect: Color,
    val wrong: Color,
    val wrongSide: Color,
    val onWrong: Color,
    /** A tile whose light went out: an answer nobody needs to look at any more. */
    val unlit: Color,
    val unlitSide: Color,
    val onUnlit: Color,
    /** Each answer's own colour, by its index, the mark its letter stands in; more answers repeat them. */
    val letters: List<Color>,
    val onLetter: Color,
    val letterUnlit: Color,
    val onLetterUnlit: Color,
    /** The seats' colours, one per member of a room, in seat order. */
    val seats: List<Color>,
    /** A split-flap cell: its face, the lower half's shade, the digit and the split between halves. */
    val flap: Color,
    val flapShade: Color,
    val onFlap: Color,
    val flapSplit: Color,
    /** A marquee bulb lit, out, and lit in the last seconds. */
    val bulbOn: Color,
    val bulbOff: Color,
    val bulbWarn: Color,
    /** Points won and lost, as text on the page. */
    val gain: Color,
    val loss: Color,
    /** The ring a keyboard's focus draws. */
    val focus: Color,
) {
    init {
        require(letters.isNotEmpty() && seats.isNotEmpty()) { "a skin names at least one letter and seat colour" }
    }

    /** The colour of the answer at [index]; the colours repeat past the last. */
    fun letter(index: Int): Color = letters[index.mod(letters.size)]

    /** The colour of the seat at [seat]; the colours repeat past the last. */
    fun seat(seat: Int): Color = seats[seat.mod(seats.size)]
}
