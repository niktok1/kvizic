package io.ntole.kvizic.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import io.ntole.kvizic.core.domain.error.DomainError
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.language.SerbianScript
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.lobby.LobbyExit
import io.ntole.kvizic.core.domain.lobby.LobbyRules
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.language.GameStrings
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.Strings
import io.ntole.kvizic.language.failureText
import io.ntole.kvizic.language.fill
import kotlin.time.Duration

/** What the room let the player go for, as Home says it; none for leaving, which they did themselves. */
internal fun GameStrings.exitText(exit: LobbyExit): String? =
    when (exit) {
        LobbyExit.LEFT, LobbyExit.UPGRADE_REQUIRED -> null
        LobbyExit.KICKED -> exitKicked
        LobbyExit.VOTED_OUT -> exitVotedOut
        LobbyExit.NOT_BACK -> exitNotBack
        LobbyExit.LOBBY_CLOSED -> exitClosed
        LobbyExit.LOBBY_GONE -> exitGone
        LobbyExit.REPLACED -> exitReplaced
        LobbyExit.SESSION_ENDED -> exitSessionEnded
        LobbyExit.SERVER_RESTARTING -> exitRestarting
        LobbyExit.CONNECTION_LOST -> exitConnectionLost
    }

/** Why a seat could not be taken, in a few words: the room's own reasons first, then any failure's. */
internal fun Strings.entryFailureText(
    error: DomainError,
    retryAfter: Duration?,
): String =
    when (error) {
        GameError.LOBBY_NOT_FOUND -> game.roomNotFound
        GameError.LOBBY_FULL -> game.roomFull
        GameError.LOBBY_BANNED -> game.roomBanned
        GameError.TOO_MANY_LOBBIES -> game.tooManyRooms
        GameError.SERVER_DRAINING -> game.serverDraining
        else -> failureText(error, retryAfter)
    }

/** A note the room shows a moment. */
internal fun GameStrings.noteText(note: RoomNote): String =
    when (note) {
        RoomNote.ONLY_HOST -> onlyHost
        RoomNote.REFUSED -> refused
        RoomNote.TOPICS_TOPPED_UP -> topicsToppedUp
        RoomNote.GAME_SHORTENED -> gameShortened
        RoomNote.SERVER_RESTARTING -> exitRestarting
        RoomNote.REPORTED -> reported
        RoomNote.REPORT_FAILED -> refused
        RoomNote.VOTE_TOO_SOON -> voteTooSoon
        RoomNote.CODE_COPIED -> codeCopied
    }

/** A reason to report a question, as the report's dialog says it. */
internal fun GameStrings.reasonText(reason: QuestionReportReason): String =
    when (reason) {
        QuestionReportReason.WRONG_ANSWER -> reportWrongAnswer
        QuestionReportReason.TYPO -> reportTypo
        QuestionReportReason.AMBIGUOUS -> reportAmbiguous
        QuestionReportReason.OFFENSIVE -> reportOffensive
        QuestionReportReason.OTHER -> reportOther
    }

/** A reaction a room may send: its id on the wire, its icon, and its name for a screen reader. */
internal data class ReactionKind(
    val id: String,
    val icon: ImageVector,
    val name: (GameStrings) -> String,
)

/** The server's reactions, in its order, each with the design system's icon for it. */
internal val REACTIONS: List<ReactionKind> by lazy {
    listOf(
        ReactionKind("bravo", KvizicIcons.ThumbUp) { it.reactionBravo },
        ReactionKind("clap", KvizicIcons.Clap) { it.reactionClap },
        ReactionKind("fire", KvizicIcons.Flame) { it.reactionFire },
        ReactionKind("wow", KvizicIcons.Wow) { it.reactionWow },
        ReactionKind("laugh", KvizicIcons.Laugh) { it.reactionLaugh },
        ReactionKind("oops", KvizicIcons.Oops) { it.reactionOops },
        ReactionKind(LobbyRules.NUDGE, KvizicIcons.Bell) { it.reactionNudge },
    )
}

/** The emotes on the room's bar: every reaction but the nudge, which a member sends with a button of its own. */
internal val EMOTES: List<ReactionKind> by lazy { REACTIONS.filter { it.id != LobbyRules.NUDGE } }

/** The icon of the reaction [id], or none for one this build does not know. */
internal fun reactionIcon(id: String): ImageVector? = REACTIONS.firstOrNull { it.id == id }?.icon

/**
 * A word of the server's in the script shown: a question, an answer, a player's name. Serbian Latin is made
 * from the Cyrillic; English shows it as written, the questions being Serbian.
 */
@Composable
@ReadOnlyComposable
internal fun shown(text: String): String = shownIn(text, LocalLanguage.current)

internal fun shownIn(
    text: String,
    language: Language,
): String = if (language == Language.SERBIAN_LATIN) SerbianScript.toLatin(text) else text

/** A topic's name in [language]: the Serbian name as the server keeps it, in Latin made from it, or the English. */
internal fun topicName(
    topic: Topic,
    language: Language,
): String =
    when (language) {
        Language.SERBIAN_CYRILLIC -> topic.nameSr
        Language.SERBIAN_LATIN -> SerbianScript.toLatin(topic.nameSr)
        Language.ENGLISH -> topic.nameEn
    }

/** The name of the topic [id] among [topics], or the id itself for one not read yet. */
internal fun topicNameOf(
    id: String,
    topics: List<Topic>,
    language: Language,
): String = topics.firstOrNull { it.id == id }?.let { topicName(it, language) } ?: id

/** The name of [level], as the settings' chips have it. */
internal fun GameStrings.levelName(level: LobbyDifficulty): String =
    when (level) {
        LobbyDifficulty.EASY -> easy
        LobbyDifficulty.MEDIUM -> medium
        LobbyDifficulty.HARD -> hard
    }

/** One of a room's settings on its chip: a few words, and an icon before them where one says it. */
internal data class SettingChip(
    val text: String,
    val icon: ImageVector? = null,
)

/** A room's settings, each in a few words, as its chips show them: its difficulty only when not medium. */
@Composable
@ReadOnlyComposable
internal fun settingsChips(
    settings: LobbySettings,
    topics: List<Topic> = emptyList(),
): List<SettingChip> {
    val words = LocalStrings.current.game
    val language = LocalLanguage.current
    return listOfNotNull(
        SettingChip(words.questionCount.of(settings.questionCount, language)),
        SettingChip(words.seconds.fill(settings.secondsPerQuestion), KvizicIcons.Clock),
        SettingChip(topicsSummary(settings.topics, topics, language, words.allTopics)),
        settings.difficulty.takeIf { it != LobbyDifficulty.MEDIUM }?.let { SettingChip(words.levelName(it)) },
        SettingChip(if (settings.wrongAnswerPenalty) words.penaltyOn else words.penaltyOff),
    )
}
