package io.ntole.kvizic.room

import io.ntole.kvizic.language.Language
import kotlin.test.Test

/**
 * The lobby and the room's dialogs with every word whole ([RoomFitChecks]) in every language but Serbian
 * Cyrillic, which [RoomFitDrawTest] draws, on the narrowest phone at the usual font and a large one, and the
 * host's choices about a member on the tablet too, where they stand in a row: a language's long word fails
 * here. The languages are shared out among [SHARDS] classes by their order, one
 * each while there are as many, since the test JVMs share out classes and never the tests of one: so they
 * draw side by side, about a minute and a half each on CI. A language added lands in one of them.
 */
abstract class RoomFitLanguagesDrawTest(
    shard: Int,
) : RoomFitChecks() {
    private val languages = OTHERS.filterIndexed { index, _ -> index % SHARDS == shard }

    @Test
    fun `the lobby's chips are whole in the other languages`() = languages.forEach { lobbyChipsWhole(it, NARROWEST) }

    @Test
    fun `the host's choices about a member are whole in the other languages`() =
        languages.forEach { memberChoicesWhole(it, MEMBER_SIZES) }

    @Test
    fun `a member's vote to put a player out is whole in the other languages`() = languages.forEach { voteWhole(it) }

    @Test
    fun `every reason to report a question is read whole in the other languages`() =
        languages.forEach { reportWhole(it) }

    internal companion object {
        /** Every language [RoomFitDrawTest] does not draw, in [Language]'s order. */
        val OTHERS = Language.entries.filter { it != Language.DEFAULT }

        /** How many classes share them out: the classes below, one for each. */
        const val SHARDS = 6

        /**
         * The narrowest phone, where the member's choices stack, and the tablet, where they stand in a row at
         * their own width: a word whole in one may be cut in the other.
         */
        val MEMBER_SIZES = NARROWEST + (TABLET to 1f)
    }
}

/** The first of [RoomFitLanguagesDrawTest.OTHERS], and every [RoomFitLanguagesDrawTest.SHARDS]th after it. */
class RoomFitLanguages1DrawTest : RoomFitLanguagesDrawTest(0)

/** The second of [RoomFitLanguagesDrawTest.OTHERS], and every [RoomFitLanguagesDrawTest.SHARDS]th after it. */
class RoomFitLanguages2DrawTest : RoomFitLanguagesDrawTest(1)

/** The third of [RoomFitLanguagesDrawTest.OTHERS], and every [RoomFitLanguagesDrawTest.SHARDS]th after it. */
class RoomFitLanguages3DrawTest : RoomFitLanguagesDrawTest(2)

/** The fourth of [RoomFitLanguagesDrawTest.OTHERS], and every [RoomFitLanguagesDrawTest.SHARDS]th after it. */
class RoomFitLanguages4DrawTest : RoomFitLanguagesDrawTest(3)

/** The fifth of [RoomFitLanguagesDrawTest.OTHERS], and every [RoomFitLanguagesDrawTest.SHARDS]th after it. */
class RoomFitLanguages5DrawTest : RoomFitLanguagesDrawTest(4)

/** The sixth of [RoomFitLanguagesDrawTest.OTHERS], and every [RoomFitLanguagesDrawTest.SHARDS]th after it. */
class RoomFitLanguages6DrawTest : RoomFitLanguagesDrawTest(5)
