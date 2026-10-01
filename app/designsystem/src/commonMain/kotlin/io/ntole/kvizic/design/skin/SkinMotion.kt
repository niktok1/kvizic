package io.ntole.kvizic.design.skin

import androidx.compose.runtime.Immutable

/**
 * How a skin moves, in milliseconds unless named otherwise. Every motion built on these is read where
 * it is drawn and never composed (the components' motion rule), and none loops forever but the
 * spinner and a running timer, which stop once they leave the screen.
 */
@Immutable
data class SkinMotion(
    /** A press sinking a surface under the finger: quick, so the press is felt at once. */
    val press: Int,
    /** The spring a surface comes back up on once let go: its damping ratio and stiffness. */
    val releaseDamping: Float,
    val releaseStiffness: Float,
    /** A surface settling at a new height or colour: a tile locked in, a tile going dark. */
    val settle: Int,
    /** A tile lighting up as the answer is revealed. */
    val reveal: Int,
    /** One flap of a split-flap digit, at most [flapsPerDigit] a digit, and the delay between two cells. */
    val flap: Int,
    val flapsPerDigit: Int,
    val flapStagger: Int,
    /** A marquee bulb's flicker as it goes out. */
    val bulbFlicker: Int,
    /** From how many seconds left a timer warns. */
    val warnSeconds: Int,
    /** A reaction's burst, whole, and one turn of the spinner. */
    val burst: Int,
    val spinnerTurn: Int,
    /** One step of a game giving way to the next: read to answered, answered to revealed, on to the next. */
    val stage: Int,
    /** An answer tile coming up as the answers open, and the delay between one tile and the next. */
    val tileAppear: Int,
    val tileStagger: Int,
) {
    init {
        require(flapsPerDigit >= 1) { "a digit flaps at least once" }
    }
}
