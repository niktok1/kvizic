package io.ntole.kvizic.server.achievement

import io.ntole.kvizic.core.region.ContentLanguages

/**
 * The achievements, each earning one title, in the order a profile lists them. A goal, the words, or a new
 * achievement over a [Count] the server keeps changes here with no client release; a goal and the words that
 * name it change together. A title once earned is kept (`AchievementStore`), so a goal may rise and an
 * achievement may retire, which leaves its words for those who wear it.
 */
object Achievements {
    private val LANGUAGES = ContentLanguages.ALL + "en"

    /** A perfect game has at least this many questions. */
    const val PERFECT_LEAST = 10

    val ALL: List<Achievement> =
        listOf(
            achievement(
                "title.lucky",
                Count.Wins,
                1,
                listOf("Срећна рука", "Sretna ruka", "Sretna ruka", "Srečna roka", "Среќна рака", "Lucky hand"),
                listOf("Прва победа", "Prva pobjeda", "Prva pobjeda", "Prva zmaga", "Прва победа", "Your first win"),
            ),
            achievement(
                "title.finger",
                Count.FirstRights,
                25,
                listOf("Брзи прст", "Brzi prst", "Brzi prst", "Hitri prst", "Брз прст", "Fast finger"),
                listOf(
                    "Најбржи тачан одговор 25 пута",
                    "Najbrži točan odgovor 25 puta",
                    "Najbrži tačan odgovor 25 puta",
                    "Najhitrejši pravilni odgovor 25-krat",
                    "Најбрз точен одговор 25 пати",
                    "The fastest right answer 25 times",
                ),
            ),
            achievement(
                "title.bookworm",
                Count.RightIn("LANGUAGE"),
                100,
                listOf("Књишки мољац", "Knjiški moljac", "Knjiški moljac", "Knjižni molj", "Книжен молец", "Bookworm"),
                listOf(
                    "100 тачних одговора из Језика и књижевности",
                    "100 točnih odgovora iz Jezika i književnosti",
                    "100 tačnih odgovora iz Jezika i književnosti",
                    "100 pravilnih odgovorov iz Jezika in književnosti",
                    "100 точни одговори од Јазик и книжевност",
                    "100 right answers in Language & literature",
                ),
            ),
            achievement(
                "title.brain",
                Count.PerfectGames,
                1,
                listOf("Мозак", "Mozak", "Mozak", "Možgani", "Мозок", "Brain"),
                listOf(
                    "Све тачно у игри од $PERFECT_LEAST или више питања",
                    "Sve točno u igri od $PERFECT_LEAST ili više pitanja",
                    "Sve tačno u igri od $PERFECT_LEAST ili više pitanja",
                    "Vse pravilno v igri z $PERFECT_LEAST ali več vprašanji",
                    "Сè точно во игра од $PERFECT_LEAST или повеќе прашања",
                    "Every answer right in a game of $PERFECT_LEAST or more questions",
                ),
            ),
            achievement(
                "title.star",
                Count.Wins,
                10,
                listOf("Звезда", "Zvijezda", "Zvijezda", "Zvezda", "Ѕвезда", "Star"),
                listOf("10 победа", "10 pobjeda", "10 pobjeda", "10 zmag", "10 победи", "10 wins"),
            ),
            achievement(
                "title.knowall",
                Count.TopicsWith(10),
                15,
                listOf("Свезналица", "Sveznalica", "Sveznalica", "Vseved", "Сезнајко", "Know-it-all"),
                listOf(
                    "По 10 тачних одговора у 15 тема",
                    "Po 10 točnih odgovora u 15 tema",
                    "Po 10 tačnih odgovora u 15 tema",
                    "Po 10 pravilnih odgovorov v 15 temah",
                    "По 10 точни одговори во 15 теми",
                    "10 right answers in each of 15 topics",
                ),
            ),
            achievement(
                "title.encyclopedia",
                Count.RightAnswers,
                1000,
                listOf(
                    "Енциклопедија",
                    "Enciklopedija",
                    "Enciklopedija",
                    "Enciklopedija",
                    "Енциклопедија",
                    "Encyclopedia",
                ),
                listOf(
                    "1000 тачних одговора",
                    "1000 točnih odgovora",
                    "1000 tačnih odgovora",
                    "1000 pravilnih odgovorov",
                    "1000 точни одговори",
                    "1000 right answers",
                ),
            ),
            achievement(
                "title.legend",
                Count.Wins,
                50,
                listOf("Легенда", "Legenda", "Legenda", "Legenda", "Легенда", "Legend"),
                listOf("50 победа", "50 pobjeda", "50 pobjeda", "50 zmag", "50 победи", "50 wins"),
            ),
        )

    private val byTitle = ALL.associateBy { it.titleId }

    /** The achievement that earns [titleId], or null for a title there is none for. */
    fun of(titleId: String): Achievement? = byTitle[titleId]

    /** The words of the title [id] for a reader of the content language [lang], the nearest there are; none for none. */
    fun titleIn(
        id: String,
        lang: String,
    ): String {
        val names = of(id)?.title ?: return ""
        return ContentLanguages.chain(lang).firstNotNullOfOrNull { names[it] }.orEmpty()
    }

    private fun achievement(
        titleId: String,
        count: Count,
        goal: Int,
        title: List<String>,
        how: List<String>,
    ) = Achievement(titleId, count, goal, LANGUAGES.zip(title).toMap(), LANGUAGES.zip(how).toMap())
}

/**
 * Reaching [goal] of [count] earns the title [titleId], whose words are [title]; [how] says what earns it. A
 * [retired] one is earned no more, and stays with those who have it.
 */
data class Achievement(
    val titleId: String,
    val count: Count,
    val goal: Int,
    val title: Map<String, String>,
    val how: Map<String, String>,
    val retired: Boolean = false,
)

/** What an achievement counts in a player's [Totals]. */
sealed interface Count {
    fun of(totals: Totals): Int

    /** Games won in a room. */
    data object Wins : Count {
        override fun of(totals: Totals) = totals.wins
    }

    /** Right answers, in rooms and solo. */
    data object RightAnswers : Count {
        override fun of(totals: Totals) = totals.rightAnswers
    }

    /** Games with every answer right, of at least [Achievements.PERFECT_LEAST] questions, in rooms and solo. */
    data object PerfectGames : Count {
        override fun of(totals: Totals) = totals.perfectGames
    }

    /** Questions the player was the first to answer right, in games of two or more. */
    data object FirstRights : Count {
        override fun of(totals: Totals) = totals.firstRights
    }

    /** Right answers to questions of [topicId]. */
    data class RightIn(
        val topicId: String,
    ) : Count {
        override fun of(totals: Totals) = totals.rightByTopic[topicId] ?: 0
    }

    /** Topics with at least [right] right answers. */
    data class TopicsWith(
        val right: Int,
    ) : Count {
        override fun of(totals: Totals) = totals.rightByTopic.values.count { it >= right }
    }
}

/** What a player's games add up to, as far as achievements count them. */
data class Totals(
    val wins: Int = 0,
    val rightAnswers: Int = 0,
    val perfectGames: Int = 0,
    val firstRights: Int = 0,
    val rightByTopic: Map<String, Int> = emptyMap(),
)
