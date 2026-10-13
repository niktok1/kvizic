package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.core.region.Country
import io.ntole.kvizic.server.config.ServerConfig
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.db.QuestionCountries
import io.ntole.kvizic.server.db.QuestionOptionTranslations
import io.ntole.kvizic.server.db.QuestionOptions
import io.ntole.kvizic.server.db.QuestionStats
import io.ntole.kvizic.server.db.QuestionTopics
import io.ntole.kvizic.server.db.QuestionTranslations
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.db.SeenQuestions
import io.ntole.kvizic.server.db.Topics
import io.ntole.kvizic.server.lobby.GameQuestion
import io.ntole.kvizic.server.lobby.PickRequest
import io.ntole.kvizic.server.lobby.PickResult
import io.ntole.kvizic.server.lobby.QuestionSource
import io.ntole.kvizic.server.lobby.QuestionText
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.case
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Picks a game's questions from the bank: approved ones in the topics asked for that every player's
 * country may see ([eligible]), those the lobby's players have seen least first (by how many of them saw
 * each, then by when last), ties at random, most at the level the room chose and the rest beside it
 * ([DifficultyMix]), each at the level it plays at ([MeasuredDifficulty]), then asked from easy to hard.
 * When the topics hold too few, the rest come from every other topic ([PickResult.toppedUp]); when the
 * whole bank does, the game is shorter ([PickResult.shortened]).
 *
 * A question is common, with no countries, or scoped to some, and then asked only when every one of the
 * players' countries is among its own: a room of one country gets that country's and the common ones, a
 * room of mixed countries the common ones alone. [localShare] of a game comes from the scoped ones its
 * topics hold, where they hold that many ([choose]), since a country's set is a small part of its pool and
 * would otherwise rarely show. Among questions seen alike, one readable in every language the players read
 * comes first, never in place of another: a game is never shorter for a missing translation, whose reader
 * reads the next text along `ContentLanguages.chain`. One question id is asked in every language, so its
 * measured level and its stats are one.
 *
 * Reads only, in one transaction, before the game starts, never while it runs.
 */
class DbQuestionSource(
    private val db: Db,
    private val random: Random = Random.Default,
    /** The part of a game taken from local questions, 0 to 1 (`LOCAL_SHARE`): 0 takes none on purpose. */
    private val localShare: Double = ServerConfig.DEFAULT_LOCAL_SHARE,
) : QuestionSource {
    init {
        require(localShare in 0.0..1.0) { "a local share is from 0 to 1, not $localShare" }
    }

    private val mix = DifficultyMix(random)

    private data class Candidate(
        val id: String,
        val level: Difficulty,
        val seenBy: Long,
        val lastSeen: Long?,
        /** Scoped to countries, every player's among them. */
        val local: Boolean,
        /** In every language the players read: its source's, or a translation made from its revision. */
        val readable: Boolean,
    )

    /** Whom a game is for: its players, the countries they play from and the content languages they read. */
    private class Audience(
        val players: Set<String>,
        val countries: Set<Country>,
        val languages: Set<String>,
    )

    override suspend fun pick(request: PickRequest): PickResult =
        db.query {
            val audience =
                Audience(
                    players = request.players,
                    countries = request.countries,
                    languages = request.languages.map(ContentLanguages::of).toSet(),
                )
            val inTopics = candidates(request.topics, audience)
            val inTopicIds = inTopics.map { it.id }.toSet()
            val others =
                if (inTopics.size < request.count && request.topics.isNotEmpty()) {
                    candidates(emptyList(), audience).filter { it.id !in inTopicIds }
                } else {
                    emptyList()
                }
            val picked = choose(request, inTopics, others)
            val ramp = picked.sortedBy { rampOrder(it.level) }
            val questions = load(ramp.associate { it.id to it.level })
            PickResult(
                questions = questions,
                toppedUp = picked.any { it.id !in inTopicIds },
                shortened = questions.size < request.count,
                soloBestBefore = request.soloPlayer?.let { soloBest(it, request.difficulty) },
            )
        }

    /**
     * The game's questions, from [inTopics] before [others]. With no local share, or a game too short for
     * one, every question as it was seen, local or common. With one, first the share from the local ones in
     * [inTopics], fewer when they hold fewer, then the rest of the game, where among questions seen alike and
     * at one level the common ones come before the local ones left over ([tiers]): a game takes about its
     * share while unseen common ones last, yet never a seen question before an unseen local one, a level
     * off for want of a common one at it, or a shorter game. The levels are the game's over both parts, and
     * the two are mixed, so no level opens with the local ones.
     */
    private fun choose(
        request: PickRequest,
        inTopics: List<Candidate>,
        others: List<Candidate>,
    ): List<Candidate> {
        val levelOf = { candidate: Candidate -> candidate.level }
        val share = (request.count * localShare).roundToInt()
        if (share == 0) return mix.pick(tiers(inTopics) + tiers(others), request.count, request.difficulty, levelOf)
        val localInTopics = inTopics.filter { it.local }
        val local =
            if (localInTopics.isEmpty()) {
                emptyList()
            } else {
                mix.pick(tiers(localInTopics), share, request.difficulty, levelOf)
            }
        val taken = local.map { it.id }.toSet()
        val rest =
            mix.pick(
                tiers(inTopics.filter { it.id !in taken }, commonFirst = true) + tiers(others, commonFirst = true),
                mix.remaining(request.difficulty, request.count, local.map(levelOf)),
                request.difficulty,
                levelOf,
            )
        return if (local.isEmpty()) rest else (local + rest).shuffled(random)
    }

    private fun candidates(
        topics: List<String>,
        audience: Audience,
    ): List<Candidate> {
        // Each seen row meets each answer's row, so both are counted distinct.
        val seenBy = SeenQuestions.playerId.countDistinct()
        val lastSeen = SeenQuestions.lastSeenAt.max()
        val options = QuestionOptions.slot.countDistinct()
        val local = flag(exists(countryRows()))
        val readable = flag(readableIn(audience.languages))
        val joined =
            Questions
                .join(
                    SeenQuestions,
                    JoinType.LEFT,
                    onColumn = Questions.id,
                    otherColumn = SeenQuestions.questionId,
                    additionalConstraint = {
                        SeenQuestions.playerId inList audience.players.ifEmpty { setOf("") }
                    },
                ).join(QuestionStats, JoinType.LEFT, Questions.id, QuestionStats.questionId)
                .join(QuestionOptions, JoinType.LEFT, Questions.id, QuestionOptions.questionId)
        val played = listOf(QuestionStats.timesAnswered, QuestionStats.timesCorrect, QuestionStats.timesUnanswered)
        val inTopics: Op<Boolean> =
            if (topics.isEmpty()) {
                Op.TRUE
            } else {
                exists(
                    QuestionTopics
                        .select(QuestionTopics.questionId)
                        .where {
                            (QuestionTopics.questionId eq Questions.id) and (QuestionTopics.topicId inList topics)
                        },
                )
            }
        // The flags read the question's language and revision, so the rows are grouped by them too.
        val grouped = listOf(Questions.id, Questions.difficulty, Questions.language, Questions.revision) + played
        return joined
            .select(listOf(Questions.id, Questions.difficulty, seenBy, lastSeen, options, local, readable) + played)
            .where { (Questions.status eq QuestionStatus.APPROVED) and inTopics and eligible(audience.countries) }
            .groupBy(*grouped.toTypedArray())
            .map { row ->
                // A question no game has asked yet has no stats row.
                Candidate(
                    id = row[Questions.id],
                    level =
                        MeasuredDifficulty.of(
                            authored = row[Questions.difficulty],
                            options = row[options].toInt(),
                            answered = row.getOrNull(QuestionStats.timesAnswered) ?: 0,
                            correct = row.getOrNull(QuestionStats.timesCorrect) ?: 0,
                            unanswered = row.getOrNull(QuestionStats.timesUnanswered) ?: 0,
                        ),
                    seenBy = row[seenBy],
                    lastSeen = row[lastSeen],
                    local = row[local] == 1,
                    readable = row[readable] == 1,
                )
            }
    }

    /**
     * Whether the question may be asked of players from [countries]: common, with no country of its own,
     * or scoped to every one of them. [Country.UNKNOWN], a country this server does not know, no question
     * is scoped to, so a room with one gets the common questions alone; so does a room of no country,
     * which no room is.
     */
    private fun eligible(countries: Set<Country>): Op<Boolean> {
        val common = notExists(countryRows())
        if (countries.isEmpty() || Country.UNKNOWN in countries) return common
        val scopedToAll =
            countries
                .sortedBy { it.ordinal }
                .map { country -> exists(countryRows { QuestionCountries.country eq country }) }
                .reduce<Op<Boolean>, Op<Boolean>> { all, next -> all and next }
        return common or scopedToAll
    }

    /** The question's own country rows, those [also] holds of when given. */
    private fun countryRows(also: (() -> Op<Boolean>)? = null): Query =
        QuestionCountries
            .select(QuestionCountries.questionId)
            .where {
                val own = QuestionCountries.questionId eq Questions.id
                if (also == null) own else own and also()
            }

    /**
     * Whether the question reads in every one of [languages]: each its source's, or one it has a
     * translation in made from its current revision. A stale one, which an edit left behind, is none.
     */
    private fun readableIn(languages: Set<String>): Op<Boolean> =
        languages
            .sorted()
            .map { language ->
                (Questions.language eq language) or
                    exists(
                        QuestionTranslations
                            .select(QuestionTranslations.questionId)
                            .where {
                                (QuestionTranslations.questionId eq Questions.id) and
                                    (QuestionTranslations.language eq language) and
                                    (QuestionTranslations.sourceRevision eq Questions.revision)
                            },
                    )
            }.reduceOrNull<Op<Boolean>, Op<Boolean>> { all, next -> all and next } ?: Op.TRUE

    /** 1 where [condition] holds, else 0: a condition as a value a grouped select reads. */
    private fun flag(condition: Op<Boolean>): ExpressionWithColumnType<Int> =
        case().When(condition, intLiteral(1)).Else(intLiteral(0))

    /**
     * [candidates] by how many of the players saw each, the fewest first; in each, with [commonFirst] the
     * common ones before the local ones, then those readable in every language the players read first, then
     * those seen longest ago. [DifficultyMix] takes a tier whole when the game has room for it, and else
     * the first at each level it wants, so a local one is taken in a tier only where the common ones at its
     * level have run out.
     */
    private fun tiers(
        candidates: List<Candidate>,
        commonFirst: Boolean = false,
    ): List<List<Candidate>> =
        candidates
            .shuffled(random)
            .sortedWith(
                compareBy<Candidate> { it.seenBy }
                    .thenBy { commonFirst && it.local }
                    .thenBy { !it.readable }
                    .thenBy { it.lastSeen ?: Long.MIN_VALUE },
            ).groupBy { it.seenBy }
            .values
            .toList()

    /**
     * The questions of [levels], in its order and each at its level, whole, with every translation made
     * from its current revision: a question stored wrong is left out rather than asked, and so is a
     * translation that is stale or has not one answer for each of the source's slots, whose reader then
     * reads the next text along `ContentLanguages.chain`.
     */
    private fun load(levels: Map<String, Difficulty>): List<GameQuestion> {
        if (levels.isEmpty()) return emptyList()
        val ids = levels.keys.toList()
        val rows =
            Questions
                .select(
                    Questions.columns,
                ).where { Questions.id inList ids }
                .associateBy { it[Questions.id] }
        val options =
            QuestionOptions
                .select(QuestionOptions.questionId, QuestionOptions.slot, QuestionOptions.text)
                .where { QuestionOptions.questionId inList ids }
                .orderBy(QuestionOptions.slot)
                .groupBy({ it[QuestionOptions.questionId] }, { it[QuestionOptions.slot] to it[QuestionOptions.text] })
        val topics =
            QuestionTopics
                .join(Topics, JoinType.INNER, QuestionTopics.topicId, Topics.id)
                .select(QuestionTopics.questionId, QuestionTopics.topicId)
                .where { QuestionTopics.questionId inList ids }
                .orderBy(Topics.createdAt to SortOrder.ASC, Topics.id to SortOrder.ASC)
                .groupBy({ it[QuestionTopics.questionId] }, { it[QuestionTopics.topicId] })
        val translations =
            QuestionTranslations
                .select(
                    QuestionTranslations.questionId,
                    QuestionTranslations.language,
                    QuestionTranslations.text,
                    QuestionTranslations.explanation,
                    QuestionTranslations.sourceRevision,
                ).where { QuestionTranslations.questionId inList ids }
                .groupBy { it[QuestionTranslations.questionId] }
        val translatedOptions =
            QuestionOptionTranslations
                .select(
                    QuestionOptionTranslations.questionId,
                    QuestionOptionTranslations.language,
                    QuestionOptionTranslations.slot,
                    QuestionOptionTranslations.text,
                ).where { QuestionOptionTranslations.questionId inList ids }
                .groupBy(
                    { it[QuestionOptionTranslations.questionId] to it[QuestionOptionTranslations.language] },
                    { it[QuestionOptionTranslations.slot] to it[QuestionOptionTranslations.text] },
                )
        return ids.mapNotNull { id ->
            val row = rows[id] ?: return@mapNotNull null
            val slots = options[id].orEmpty()
            val correct = slots.indexOfFirst { (slot, _) -> slot == row[Questions.correctSlot] }
            val topic = topics[id]?.firstOrNull() ?: return@mapNotNull null
            val texts =
                translations[id]
                    .orEmpty()
                    .filter { translation ->
                        translation[QuestionTranslations.sourceRevision] == row[Questions.revision] &&
                            translation[QuestionTranslations.language] != row[Questions.language]
                    }.mapNotNull { translation ->
                        val language = translation[QuestionTranslations.language]
                        text(translation, slots.map { it.first }, translatedOptions[id to language].orEmpty())
                            ?.let { language to it }
                    }.toMap()
            runCatching {
                GameQuestion(
                    questionId = id,
                    text = row[Questions.text],
                    options = slots.map { it.second },
                    correct = correct,
                    topicId = topic,
                    kind = row[Questions.kind],
                    difficulty = levels.getValue(id),
                    explanation = row[Questions.explanation],
                    language = row[Questions.language],
                    texts = texts,
                )
            }.getOrNull()
        }
    }

    /**
     * A translation's words, its answers in the order of the source's [slots], or null unless [options]
     * hold exactly one answer for each of them.
     */
    private fun text(
        translation: ResultRow,
        slots: List<Int>,
        options: List<Pair<Int, String>>,
    ): QuestionText? {
        val bySlot = options.toMap()
        if (options.size != slots.size || bySlot.keys != slots.toSet()) return null
        return QuestionText(
            text = translation[QuestionTranslations.text],
            options = slots.map(bySlot::getValue),
            explanation = translation[QuestionTranslations.explanation],
        )
    }

    /** The best solo run of [playerId] at [level] so far, or null before one. */
    private fun soloBest(
        playerId: String,
        level: Difficulty,
    ): Int? {
        val best = Profiles.soloBest(level).first
        return Profiles
            .select(best)
            .where { Profiles.playerId eq playerId }
            .firstOrNull()
            ?.get(best)
    }

    private fun rampOrder(difficulty: Difficulty): Int =
        when (difficulty) {
            Difficulty.EASY -> 0
            Difficulty.MEDIUM, Difficulty.UNKNOWN -> 1
            Difficulty.HARD -> 2
        }
}
