package io.ntole.kvizic.server.db

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.player.NameSource
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.core.question.QuestionKind
import io.ntole.kvizic.core.question.QuestionStatus
import io.ntole.kvizic.core.report.ReportReason
import io.ntole.kvizic.core.report.ReportResolution
import io.ntole.kvizic.server.auth.IdentityProvider
import io.ntole.kvizic.server.report.ReportStatus
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

/*
 * Times are epoch milliseconds, never SQL timestamps: that keeps the schema free of the timezone
 * differences between the H2 and PostgreSQL drivers. Every foreign key to a player cascades, so deleting
 * a player's row deletes everything of theirs (`AccountDeletion`), and every foreign key leads an index,
 * which PostgreSQL does not make by itself (`ForeignKeyIndexTest`). The migrations build exactly these
 * definitions (`SchemaDriftTest`).
 */

/** Every player, a guest or signed in with Play Games: the platform's part of them. */
object Players : Table("players") {
    val id = varchar("id", 36)
    val createdAt = long("created_at")

    /** The name other players see, cleaned (`DisplayNames`): the Play Games name, or a generated nickname. */
    val displayName = varchar("display_name", DISPLAY_NAME_COLUMN)
    val nameSource = enumerationByName<NameSource>("name_source", 16)

    /** When the player last did anything with the server, kept to the minute (`PlayerStore.touch`); V10, null before. */
    val lastSeenAt = long("last_seen_at").nullable()

    override val primaryKey = PrimaryKey(id)

    /** Wide enough for [KvizicApi.Limits.MAX_DISPLAY_NAME_LENGTH] code points in UTF-16 units. */
    private const val DISPLAY_NAME_COLUMN = 64
}

/**
 * A player's sessions, one refresh-token family per device, each rotating on its own (`SessionStore`).
 * Only a hash of each token is stored.
 */
object Sessions : Table("sessions") {
    val id = varchar("id", 36)
    val playerId = varchar("player_id", 36).references(Players.id, onDelete = ReferenceOption.CASCADE)
    val refreshTokenHash = varchar("refresh_token_hash", 64)
    val refreshTokenExpiresAt = long("refresh_token_expires_at")

    /** The token the last rotation displaced, which one more refresh may still spend (the grace). */
    val previousRefreshTokenHash = varchar("previous_refresh_token_hash", 64).nullable()
    val previousRefreshTokenExpiresAt = long("previous_refresh_token_expires_at").nullable()
    val previousRefreshTokenRotatedAt = long("previous_refresh_token_rotated_at").nullable()
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = true, refreshTokenHash)
        index(isUnique = true, previousRefreshTokenHash)
        index(isUnique = false, playerId, id)
    }
}

/** Players of other services linked to players here: a Play Games player signs in as theirs. */
object Identities : Table("identities") {
    val provider = enumerationByName<IdentityProvider>("provider", 16)

    /** The service's own id for its player. */
    val subject = varchar("subject", MAX_SUBJECT_LENGTH)
    val playerId = varchar("player_id", 36).references(Players.id, onDelete = ReferenceOption.CASCADE)
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(provider, subject)

    init {
        // One link per service per player; also what finds a player's links.
        index(isUnique = true, playerId, provider)
    }

    const val MAX_SUBJECT_LENGTH: Int = 255
}

/** The game's part of a player: their avatar and what their finished games add up to. */
object Profiles : Table("profiles") {
    val playerId = varchar("player_id", 36).references(Players.id, onDelete = ReferenceOption.CASCADE)
    val avatarId = varchar("avatar_id", KvizicApi.Limits.MAX_ID_LENGTH)
    val gamesPlayed = integer("games_played").default(0)
    val gamesWon = integer("games_won").default(0)
    val answersGiven = integer("answers_given").default(0)
    val answersCorrect = integer("answers_correct").default(0)
    val soloRuns = integer("solo_runs").default(0)

    /** Experience earned in finished games in a room, none in solo (`Levels`). */
    val xp = integer("xp").default(0)

    /** The best solo run at medium, and when: every run was medium before V5, so V1's columns hold it. */
    val soloBestScore = integer("solo_best_score").nullable()
    val soloBestAt = long("solo_best_at").nullable()
    val soloBestEasyScore = integer("solo_best_easy_score").nullable()
    val soloBestEasyAt = long("solo_best_easy_at").nullable()
    val soloBestHardScore = integer("solo_best_hard_score").nullable()
    val soloBestHardAt = long("solo_best_hard_at").nullable()

    override val primaryKey = PrimaryKey(playerId)

    /** The columns of the best solo run at [level] and of when it was: medium's for a level with no name. */
    fun soloBest(level: Difficulty): Pair<Column<Int?>, Column<Long?>> =
        when (level) {
            Difficulty.EASY -> soloBestEasyScore to soloBestEasyAt
            Difficulty.HARD -> soloBestHardScore to soloBestHardAt
            Difficulty.MEDIUM, Difficulty.UNKNOWN -> soloBestScore to soloBestAt
        }
}

/**
 * What topics are grouped under in the picker, so hundreds stay easy to find: Знање, Забава and the rest.
 * Server data, in [listOrder]'s order; a topic in none is listed after every group.
 */
object TopicGroups : Table("topic_groups") {
    val id = varchar("id", KvizicApi.Limits.MAX_ID_LENGTH)
    val nameSr = varchar("name_sr", KvizicApi.Limits.MAX_TOPIC_NAME_LENGTH)
    val nameEn = varchar("name_en", KvizicApi.Limits.MAX_TOPIC_NAME_LENGTH)
    val listOrder = integer("list_order")

    override val primaryKey = PrimaryKey(id)
}

/** The topics questions are filed under. Never deleted; the order of topics is when each was added. */
object Topics : Table("topics") {
    val id = varchar("id", KvizicApi.Limits.MAX_ID_LENGTH)
    val nameSr = varchar("name_sr", KvizicApi.Limits.MAX_TOPIC_NAME_LENGTH)
    val nameEn = varchar("name_en", KvizicApi.Limits.MAX_TOPIC_NAME_LENGTH)
    val createdAt = long("created_at")

    /** The group the topic is listed under, or none. */
    val groupId = varchar("group_id", KvizicApi.Limits.MAX_ID_LENGTH).references(TopicGroups.id).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, groupId, id)
    }
}

/**
 * The question bank. Only an [QuestionStatus.APPROVED] question is asked. Every edit moves [revision]
 * on, which is what an edit's compare-and-set and a report name. [sourceUrl] is the moderator's alone.
 */
object Questions : Table("questions") {
    val id = varchar("id", 36)
    val status = enumerationByName<QuestionStatus>("status", 16)
    val kind = enumerationByName<QuestionKind>("kind", 16)
    val text = varchar("text", TEXT_COLUMN)

    /** Which of [QuestionOptions] is right, by its slot. */
    val correctSlot = integer("correct_slot")
    val difficulty = enumerationByName<Difficulty>("difficulty", 16)
    val explanation = varchar("explanation", EXPLANATION_COLUMN).nullable()
    val sourceUrl = varchar("source_url", KvizicApi.Limits.MAX_SOURCE_URL_LENGTH).nullable()
    val language = varchar("lang", 16)
    val author = varchar("author", 64)
    val importBatch = varchar("import_batch", 64).nullable()

    /** The draft's key: an import that finds it here is a duplicate, never a second question. */
    val importKey = varchar("import_key", KvizicApi.Limits.MAX_IMPORT_KEY_LENGTH).nullable()
    val revision = integer("revision").default(1)
    val createdAt = long("created_at")
    val updatedAt = long("updated_at")
    val reviewedAt = long("reviewed_at").nullable()
    val rejectionReason = varchar("rejection_reason", KvizicApi.Limits.MAX_REASON_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = true, importKey)
        index(isUnique = false, status, id)
    }

    /** Wider than [KvizicApi.Limits.MAX_QUESTION_TEXT_LENGTH], so the limit can rise this far with no migration. */
    const val TEXT_COLUMN = 200

    /** Wider than [KvizicApi.Limits.MAX_EXPLANATION_LENGTH], which was 300 when V1 made the column. */
    const val EXPLANATION_COLUMN = 300
}

/** A question's 2 to 4 answers, by slot, from 0: never a fixed number of columns. */
object QuestionOptions : Table("question_options") {
    val questionId = varchar("question_id", 36).references(Questions.id, onDelete = ReferenceOption.CASCADE)
    val slot = integer("slot")
    val text = varchar("text", TEXT_COLUMN)

    override val primaryKey = PrimaryKey(questionId, slot)

    /** Wider than [KvizicApi.Limits.MAX_OPTION_LENGTH], so the limit can rise this far with no migration. */
    const val TEXT_COLUMN = 80
}

object QuestionTopics : Table("question_topics") {
    val questionId = varchar("question_id", 36).references(Questions.id, onDelete = ReferenceOption.CASCADE)
    val topicId = varchar("topic_id", KvizicApi.Limits.MAX_ID_LENGTH).references(Topics.id)

    override val primaryKey = PrimaryKey(questionId, topicId)

    init {
        index(isUnique = false, topicId, questionId)
    }
}

/**
 * How each question has played, in a table of its own, so the game end's counters never rewrite a
 * question's row. Moved only by SQL increments (`ResultWriter`).
 */
object QuestionStats : Table("question_stats") {
    val questionId = varchar("question_id", 36).references(Questions.id, onDelete = ReferenceOption.CASCADE)
    val timesShown = integer("times_shown").default(0)
    val timesAnswered = integer("times_answered").default(0)
    val timesCorrect = integer("times_correct").default(0)
    val totalCorrectMs = long("total_correct_ms").default(0)
    val lastShownAt = long("last_shown_at").nullable()

    /** The players it waited for who gave no answer (V4): a silence is not knowing. */
    val timesUnanswered = integer("times_unanswered").default(0)

    override val primaryKey = PrimaryKey(questionId)
}

/** Which questions each player has been asked, and when last, so a game asks what the lobby saw least. */
object SeenQuestions : Table("seen_questions") {
    val playerId = varchar("player_id", 36).references(Players.id, onDelete = ReferenceOption.CASCADE)
    val questionId = varchar("question_id", 36).references(Questions.id, onDelete = ReferenceOption.CASCADE)
    val lastSeenAt = long("last_seen_at")
    val timesSeen = integer("times_seen").default(1)

    override val primaryKey = PrimaryKey(playerId, questionId)

    init {
        index(isUnique = false, questionId, playerId)
        index(isUnique = false, lastSeenAt)
    }
}

/** Every finished game. */
object Matches : Table("matches") {
    val id = varchar("id", 36)
    val kind = enumerationByName<LobbyKind>("kind", 16)
    val startedAt = long("started_at")
    val endedAt = long("ended_at")
    val questionCount = integer("question_count")
    val secondsPerQuestion = integer("seconds_per_question")

    /** The topics played, comma-separated ids, empty for all of them. */
    val topics = varchar("topics", MATCH_TOPICS_COLUMN)
    val participants = integer("participants")
    val endedEarly = bool("ended_early")

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = false, endedAt)
    }

    private const val MATCH_TOPICS_COLUMN = 400
}

object MatchPlayers : Table("match_players") {
    val matchId = varchar("match_id", 36).references(Matches.id, onDelete = ReferenceOption.CASCADE)
    val playerId = varchar("player_id", 36).references(Players.id, onDelete = ReferenceOption.CASCADE)
    val score = integer("score")
    val correct = integer("correct")
    val answered = integer("answered")
    val standing = integer("standing")

    /** False for a player who left before the end. */
    val finished = bool("finished")

    override val primaryKey = PrimaryKey(matchId, playerId)

    init {
        index(isUnique = false, playerId, matchId)
    }
}

object MatchQuestions : Table("match_questions") {
    val matchId = varchar("match_id", 36).references(Matches.id, onDelete = ReferenceOption.CASCADE)
    val slot = integer("slot")
    val questionId = varchar("question_id", 36).references(Questions.id, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(matchId, slot)

    init {
        index(isUnique = false, questionId, matchId)
    }
}

/** How each player does in each topic, for their best topic. Moved only by SQL increments. */
object PlayerTopicStats : Table("player_topic_stats") {
    val playerId = varchar("player_id", 36).references(Players.id, onDelete = ReferenceOption.CASCADE)
    val topicId = varchar("topic_id", KvizicApi.Limits.MAX_ID_LENGTH).references(Topics.id)
    val answered = integer("answered").default(0)
    val correct = integer("correct").default(0)

    override val primaryKey = PrimaryKey(playerId, topicId)

    init {
        index(isUnique = false, topicId, playerId)
    }
}

/**
 * Players' reports of questions: one per player per revision of a question. A reporter's account going
 * leaves the report, with nobody as its reporter, for the moderator to see.
 */
object Reports : Table("reports") {
    val id = varchar("id", 36)
    val questionId = varchar("question_id", 36).references(Questions.id, onDelete = ReferenceOption.CASCADE)
    val reporterId =
        varchar("reporter_id", 36).references(Players.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val reason = enumerationByName<ReportReason>("reason", 16)
    val questionRevision = integer("question_revision")
    val status = enumerationByName<ReportStatus>("status", 16)
    val resolution = enumerationByName<ReportResolution>("resolution", 16).nullable()
    val createdAt = long("created_at")
    val resolvedAt = long("resolved_at").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index(isUnique = true, questionId, reporterId, questionRevision)
        index(isUnique = false, status, createdAt)
        index(isUnique = false, questionId, status)
        index(isUnique = false, reporterId)
    }
}

/** Every table, in an order a create can follow. */
val appTables: Array<Table> =
    arrayOf(
        Players,
        Sessions,
        Identities,
        Profiles,
        TopicGroups,
        Topics,
        Questions,
        QuestionOptions,
        QuestionTopics,
        QuestionStats,
        SeenQuestions,
        Matches,
        MatchPlayers,
        MatchQuestions,
        PlayerTopicStats,
        Reports,
    )
