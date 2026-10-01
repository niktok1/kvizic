package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.server.db.Db
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.db.QuestionOptions
import io.ntole.kvizic.server.db.QuestionStats
import io.ntole.kvizic.server.db.QuestionTopics
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.db.SeenQuestions
import io.ntole.kvizic.server.db.Topics
import io.ntole.kvizic.server.lobby.GameQuestion
import io.ntole.kvizic.server.lobby.PickRequest
import io.ntole.kvizic.server.lobby.PickResult
import io.ntole.kvizic.server.lobby.QuestionSource
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.countDistinct
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.random.Random

/**
 * Picks a game's questions from the bank: approved ones in the topics asked for, those the lobby's
 * players have seen least first (by how many of them saw each, then by when last), ties at random, most
 * at the level the room chose and the rest beside it ([DifficultyMix]), each at the level it plays at
 * ([MeasuredDifficulty]), then asked from easy to hard. When the topics hold too few, the rest come from
 * every other topic ([PickResult.toppedUp]); when the whole bank does, the game is shorter
 * ([PickResult.shortened]).
 *
 * Reads only, in one transaction, before the game starts, never while it runs.
 */
class DbQuestionSource(
    private val db: Db,
    private val random: Random = Random.Default,
) : QuestionSource {
    private val mix = DifficultyMix(random)

    private data class Candidate(
        val id: String,
        val level: Difficulty,
        val seenBy: Long,
        val lastSeen: Long?,
    )

    override suspend fun pick(request: PickRequest): PickResult =
        db.query {
            val inTopics = candidates(request.topics, request.players)
            val inTopicIds = inTopics.map { it.id }.toSet()
            val others =
                if (inTopics.size < request.count && request.topics.isNotEmpty()) {
                    candidates(emptyList(), request.players).filter { it.id !in inTopicIds }
                } else {
                    emptyList()
                }
            val picked = mix.pick(tiers(inTopics) + tiers(others), request.count, request.difficulty) { it.level }
            val ramp = picked.sortedBy { rampOrder(it.level) }
            val questions = load(ramp.associate { it.id to it.level })
            PickResult(
                questions = questions,
                toppedUp = picked.any { it.id !in inTopicIds },
                shortened = questions.size < request.count,
                soloBestBefore = request.soloPlayer?.let { soloBest(it, request.difficulty) },
            )
        }

    private fun candidates(
        topics: List<String>,
        players: Set<String>,
    ): List<Candidate> {
        // Each seen row meets each answer's row, so both are counted distinct.
        val seenBy = SeenQuestions.playerId.countDistinct()
        val lastSeen = SeenQuestions.lastSeenAt.max()
        val options = QuestionOptions.slot.countDistinct()
        val joined =
            Questions
                .join(
                    SeenQuestions,
                    JoinType.LEFT,
                    onColumn = Questions.id,
                    otherColumn = SeenQuestions.questionId,
                    additionalConstraint = { SeenQuestions.playerId inList players.ifEmpty { setOf("") } },
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
        return joined
            .select(listOf(Questions.id, Questions.difficulty, seenBy, lastSeen, options) + played)
            .where { (Questions.status eq QuestionStatus.APPROVED) and inTopics }
            .groupBy(*(listOf(Questions.id, Questions.difficulty) + played).toTypedArray())
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
                )
            }
    }

    /** [candidates] by how many of the players saw each, the fewest first, those seen longest ago first in each. */
    private fun tiers(candidates: List<Candidate>): List<List<Candidate>> =
        candidates
            .shuffled(random)
            .sortedWith(compareBy<Candidate> { it.seenBy }.thenBy { it.lastSeen ?: Long.MIN_VALUE })
            .groupBy { it.seenBy }
            .values
            .toList()

    /**
     * The questions of [levels], in its order and each at its level, whole: a question stored wrong is
     * left out rather than asked.
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
        return ids.mapNotNull { id ->
            val row = rows[id] ?: return@mapNotNull null
            val slots = options[id].orEmpty()
            val correct = slots.indexOfFirst { (slot, _) -> slot == row[Questions.correctSlot] }
            val topic = topics[id]?.firstOrNull() ?: return@mapNotNull null
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
                )
            }.getOrNull()
        }
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
