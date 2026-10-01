package io.ntole.kvizic.server.question

import io.ntole.kvizic.core.player.NameSource
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.db.QuestionOptions
import io.ntole.kvizic.server.db.QuestionStats
import io.ntole.kvizic.server.db.QuestionTopics
import io.ntole.kvizic.server.db.Questions
import io.ntole.kvizic.server.db.SeenQuestions
import io.ntole.kvizic.server.db.Topics
import org.jetbrains.exposed.v1.jdbc.insert

/** Test rows for the question bank, written straight to the tables. Each must run inside a transaction. */
internal object BankFixtures {
    fun topic(
        id: String,
        createdAt: Long = 0,
    ) {
        Topics.insert { row ->
            row[Topics.id] = id
            row[nameSr] = id
            row[nameEn] = id
            row[Topics.createdAt] = createdAt
        }
    }

    fun question(
        id: String,
        topics: List<String>,
        options: List<String> = listOf("$id-right", "$id-a", "$id-b", "$id-c"),
        correct: Int = 0,
        status: QuestionStatus = QuestionStatus.APPROVED,
        difficulty: Difficulty = Difficulty.MEDIUM,
        explanation: String? = null,
    ) {
        Questions.insert { row ->
            row[Questions.id] = id
            row[Questions.status] = status
            row[kind] = io.ntole.kvizic.core.question.QuestionKind.CHOICE
            row[text] = "Питање $id?"
            row[correctSlot] = correct
            row[Questions.difficulty] = difficulty
            row[Questions.explanation] = explanation
            row[language] = "sr-Cyrl"
            row[author] = "test"
            row[createdAt] = 0
            row[updatedAt] = 0
        }
        options.forEachIndexed { index, option ->
            QuestionOptions.insert { row ->
                row[questionId] = id
                row[slot] = index
                row[text] = option
            }
        }
        topics.forEach { topic ->
            QuestionTopics.insert { row ->
                row[questionId] = id
                row[topicId] = topic
            }
        }
    }

    fun player(
        id: String,
        soloBest: Int? = null,
    ) {
        Players.insert { row ->
            row[Players.id] = id
            row[createdAt] = 0
            row[displayName] = id
            row[nameSource] = NameSource.GENERATED
        }
        Profiles.insert { row ->
            row[playerId] = id
            row[avatarId] = "fox"
            row[soloBestScore] = soloBest
        }
    }

    /** How question [questionId] has played: [answered] answers, [correct] of them right, [unanswered] silences. */
    fun played(
        questionId: String,
        answered: Int,
        correct: Int,
        unanswered: Int = 0,
    ) {
        QuestionStats.insert { row ->
            row[QuestionStats.questionId] = questionId
            row[timesShown] = answered + unanswered
            row[timesAnswered] = answered
            row[timesCorrect] = correct
            row[timesUnanswered] = unanswered
        }
    }

    fun seen(
        playerId: String,
        questionId: String,
        at: Long,
    ) {
        SeenQuestions.insert { row ->
            row[SeenQuestions.playerId] = playerId
            row[SeenQuestions.questionId] = questionId
            row[lastSeenAt] = at
        }
    }
}
