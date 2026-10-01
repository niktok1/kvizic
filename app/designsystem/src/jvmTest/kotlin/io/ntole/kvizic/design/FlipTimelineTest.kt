package io.ntole.kvizic.design

import io.ntole.kvizic.design.component.FlipTimeline
import io.ntole.kvizic.design.skin.SkinMotion
import io.ntole.kvizic.design.skin.Skins
import kotlin.test.Test
import kotlin.test.assertEquals

/** What a cell of split flaps shows on its way from one number to the next. */
class FlipTimelineTest {
    @Test
    fun `a countdown flaps each second straight to the next in every skin`() {
        Skins.ALL.forEach { skin ->
            (7 downTo 1).forEach { second ->
                assertEquals(
                    listOf('0' + second, '0' + second - 1),
                    faces("$second", "${second - 1}", skin.motion),
                    "${skin.id}: $second to ${second - 1}",
                )
            }
        }
    }

    @Test
    fun `digits turn up the drum as the number rises and down it as it falls`() {
        // Three flaps a digit at the most.
        val motion = Skins.Buzzers.motion
        assertEquals(listOf('3', '4', '5', '9'), faces("3", "9", motion))
        assertEquals(listOf('9', '8', '7', '3'), faces("9", "3", motion))
        assertEquals(listOf('9', '0'), faces("9", "10", motion), "a rise round past 9")
        assertEquals(listOf('0', '9'), faces("10", "9", motion), "a fall round past 0")
        // A score that falls, 120 to 85: its ones and its tens turn down, never up.
        assertEquals(listOf('0', '9', '8', '5'), faces("120", "85", motion, fromRight = 0))
        assertEquals(listOf('2', '1', '0', '8'), faces("120", "85", motion, fromRight = 1))
        // A loss growing, −25 to −30: the digits stand further round, so they turn up.
        assertEquals(listOf('5', '6', '7', '0'), faces("−25", "−30", motion, fromRight = 0))
    }

    /** The faces cell [fromRight] shows, in turn, as it flips from [from] to [to]. */
    private fun faces(
        from: String,
        to: String,
        motion: SkinMotion,
        fromRight: Int = 0,
    ): List<Char?> {
        val timeline = FlipTimeline(from)
        timeline.moveOn(to, shownAt = 0f, motion = motion)
        val total = timeline.total(motion).toInt()
        val shown = (0..total).map { millis -> timeline.at(fromRight, millis.toFloat(), motion).first }
        return shown.filterIndexed { i, face -> i == 0 || face != shown[i - 1] }
    }
}
