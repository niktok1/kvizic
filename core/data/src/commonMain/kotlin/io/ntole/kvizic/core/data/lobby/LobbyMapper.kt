package io.ntole.kvizic.core.data.lobby

import io.ntole.kvizic.core.domain.lobby.AnswerResult
import io.ntole.kvizic.core.domain.lobby.AskedQuestion
import io.ntole.kvizic.core.domain.lobby.Deadline
import io.ntole.kvizic.core.domain.lobby.FinalStanding
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.GameResults
import io.ntole.kvizic.core.domain.lobby.Lobby
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.lobby.LobbyExit
import io.ntole.kvizic.core.domain.lobby.LobbyKind
import io.ntole.kvizic.core.domain.lobby.LobbyMember
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.LobbyVisibility
import io.ntole.kvizic.core.domain.lobby.NoticeKind
import io.ntole.kvizic.core.domain.lobby.PersonalBest
import io.ntole.kvizic.core.domain.lobby.PublicLobbies
import io.ntole.kvizic.core.domain.lobby.PublicLobby
import io.ntole.kvizic.core.domain.lobby.QuestionKind
import io.ntole.kvizic.core.domain.lobby.RefusalReason
import io.ntole.kvizic.core.domain.lobby.Reveal
import io.ntole.kvizic.core.domain.lobby.Standing
import io.ntole.kvizic.core.lobby.LobbyListDto
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.PublicLobbyDto
import io.ntole.kvizic.core.lobby.Visibility
import io.ntole.kvizic.core.protocol.AnswerResultView
import io.ntole.kvizic.core.protocol.CloseCodes
import io.ntole.kvizic.core.protocol.CloseReason
import io.ntole.kvizic.core.protocol.FinalStandingView
import io.ntole.kvizic.core.protocol.LobbyView
import io.ntole.kvizic.core.protocol.MemberView
import io.ntole.kvizic.core.protocol.PhaseView
import io.ntole.kvizic.core.protocol.QuestionView
import io.ntole.kvizic.core.protocol.RejectCode
import io.ntole.kvizic.core.protocol.ResultsView
import io.ntole.kvizic.core.protocol.RevealView
import io.ntole.kvizic.core.protocol.StandingView
import io.ntole.kvizic.core.question.Difficulty
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.milliseconds
import io.ntole.kvizic.core.lobby.LobbyKind as WireLobbyKind
import io.ntole.kvizic.core.protocol.NoticeKind as WireNoticeKind
import io.ntole.kvizic.core.question.QuestionKind as WireQuestionKind

/* The realtime protocol's views as the domain has them. Every time on the wire is how long is left,
 * and is anchored here at [at], when its message arrived. */

internal fun LobbySettingsDto.toDomain(): LobbySettings =
    LobbySettings(
        questionCount = questionCount,
        secondsPerQuestion = secondsPerQuestion,
        topics = topics,
        maxPlayers = maxPlayers,
        visibility = if (visibility == Visibility.PUBLIC) LobbyVisibility.PUBLIC else LobbyVisibility.PRIVATE,
        wrongAnswerPenalty = wrongAnswerPenalty,
        difficulty =
            when (difficulty) {
                Difficulty.EASY -> LobbyDifficulty.EASY
                Difficulty.HARD -> LobbyDifficulty.HARD
                Difficulty.MEDIUM, Difficulty.UNKNOWN -> LobbyDifficulty.MEDIUM
            },
        name = name,
    )

internal fun LobbySettings.toDto(): LobbySettingsDto =
    LobbySettingsDto(
        questionCount = questionCount,
        secondsPerQuestion = secondsPerQuestion,
        topics = topics,
        maxPlayers = maxPlayers,
        visibility = if (visibility == LobbyVisibility.PUBLIC) Visibility.PUBLIC else Visibility.PRIVATE,
        wrongAnswerPenalty = wrongAnswerPenalty,
        difficulty =
            when (difficulty) {
                LobbyDifficulty.EASY -> Difficulty.EASY
                LobbyDifficulty.MEDIUM -> Difficulty.MEDIUM
                LobbyDifficulty.HARD -> Difficulty.HARD
            },
        name = name,
    )

internal fun WireLobbyKind.toDomain(settings: LobbySettings): LobbyKind =
    when (this) {
        WireLobbyKind.SOLO -> {
            LobbyKind.SOLO
        }

        WireLobbyKind.PUBLIC -> {
            LobbyKind.PUBLIC
        }

        WireLobbyKind.PRIVATE -> {
            LobbyKind.PRIVATE
        }

        WireLobbyKind.UNKNOWN -> {
            if (settings.visibility ==
                LobbyVisibility.PUBLIC
            ) {
                LobbyKind.PUBLIC
            } else {
                LobbyKind.PRIVATE
            }
        }
    }

internal fun MemberView.toDomain(): LobbyMember =
    LobbyMember(
        playerId = player,
        name = name,
        avatar = avatar,
        seat = seat,
        connected = connected,
        onResults = onResults,
        playing = playing,
        kickVotes = kickVotes,
        kickVotesNeeded = kickVotesNeeded,
        kickVotedByYou = kickVoted,
    )

internal fun LobbyView.toDomain(): Lobby {
    val settings = settings.toDomain()
    return Lobby(
        id = id,
        code = code,
        kind = kind.toDomain(settings),
        settings = settings,
        host = host.ifEmpty { null },
        members = members.map { it.toDomain() }.sortedBy { it.seat },
    )
}

internal fun QuestionView.toDomain(): AskedQuestion =
    AskedQuestion(
        index = index,
        count = count,
        text = text,
        topic = topic,
        optionCount = optionCount,
        kind =
            when (kind) {
                WireQuestionKind.CHOICE -> QuestionKind.CHOICE
                WireQuestionKind.TRUE_FALSE -> QuestionKind.TRUE_FALSE
                WireQuestionKind.UNKNOWN -> QuestionKind.UNKNOWN
            },
    )

internal fun StandingView.toDomain(): Standing = Standing(player, score, rank, correct)

internal fun AnswerResultView.toDomain(): AnswerResult = AnswerResult(player, option, points, timeMs, order)

internal fun RevealView.toDomain(): Reveal =
    Reveal(
        index = index,
        count = count,
        questionId = questionId,
        text = text,
        options = options,
        correct = correct,
        results = results.map { it.toDomain() },
        standings = standings.map { it.toDomain() },
        explanation = explanation,
        last = last,
    )

internal fun FinalStandingView.toDomain(): FinalStanding =
    FinalStanding(player, name, avatar, score, correct, rank, finished)

internal fun ResultsView.toDomain(): GameResults =
    GameResults(
        gameId = gameId,
        questionCount = questionCount,
        standings = standings.map { it.toDomain() },
        endedEarly = endedEarly,
        personalBest = personalBest?.let { PersonalBest(it.score, it.previous, it.isNew) },
    )

internal fun deadline(
    at: ComparableTimeMark,
    remainingMs: Long,
    totalMs: Long = remainingMs,
): Deadline =
    Deadline(endsAt = at + remainingMs.coerceAtLeast(0).milliseconds, total = totalMs.coerceAtLeast(0).milliseconds)

/** A phase as a snapshot has it, anchored at [at]; null for one this build does not know. */
internal fun PhaseView.toDomain(at: ComparableTimeMark): GamePhase? =
    when (this) {
        is PhaseView.Waiting -> {
            GamePhase.Waiting(lastResults?.toDomain())
        }

        is PhaseView.Countdown -> {
            GamePhase.Countdown(deadline(at, remainingMs), lastResults?.toDomain())
        }

        is PhaseView.Reading -> {
            GamePhase.Reading(
                gameId,
                players,
                question.toDomain(),
                deadline(at, remainingMs),
                standings.map { it.toDomain() },
            )
        }

        is PhaseView.Answering -> {
            GamePhase.Answering(
                gameId = gameId,
                players = players,
                question = question.toDomain(),
                options = options,
                deadline = deadline(at, remainingMs, durationMs),
                answered = answered.toSet(),
                myPick = yourPick,
                picks = picks.associate { it.player to it.option },
                standings = standings.map { it.toDomain() },
            )
        }

        is PhaseView.Revealing -> {
            GamePhase.Revealing(gameId, players, reveal.toDomain(), deadline(at, remainingMs, reveal.nextInMs))
        }

        PhaseView.Unknown -> {
            null
        }
    }

internal fun WireNoticeKind.toDomain(): NoticeKind =
    when (this) {
        WireNoticeKind.SERVER_RESTARTING -> NoticeKind.SERVER_RESTARTING
        WireNoticeKind.TOPICS_TOPPED_UP -> NoticeKind.TOPICS_TOPPED_UP
        WireNoticeKind.GAME_SHORTENED -> NoticeKind.GAME_SHORTENED
        WireNoticeKind.HOST_IDLE -> NoticeKind.HOST_IDLE
        WireNoticeKind.ROOM_IDLE -> NoticeKind.ROOM_IDLE
        WireNoticeKind.UNKNOWN -> NoticeKind.UNKNOWN
    }

internal fun RejectCode.toDomain(): RefusalReason =
    when (this) {
        RejectCode.NOT_HOST -> RefusalReason.NOT_HOST
        RejectCode.WRONG_PHASE, RejectCode.WRONG_QUESTION, RejectCode.TOO_EARLY -> RefusalReason.WRONG_PHASE
        RejectCode.ALREADY_ANSWERED -> RefusalReason.ALREADY_ANSWERED
        RejectCode.TOO_LATE -> RefusalReason.TOO_LATE
        RejectCode.INVALID_OPTION, RejectCode.UNKNOWN -> RefusalReason.UNKNOWN
        RejectCode.INVALID_SETTINGS -> RefusalReason.INVALID_SETTINGS
        RejectCode.NO_SUCH_PLAYER -> RefusalReason.NO_SUCH_PLAYER
        RejectCode.NOT_PLAYING -> RefusalReason.NOT_PLAYING
        RejectCode.RATE_LIMITED -> RefusalReason.RATE_LIMITED
        RejectCode.DRAINING -> RefusalReason.SERVER_DRAINING
        RejectCode.TOO_SOON -> RefusalReason.TOO_SOON
    }

/**
 * What a socket's close means for the player: an exit, or null for a close to reconnect after, as a
 * connection lost is. [said] is what the server's Closing message named before, if it sent one.
 */
internal fun exitFor(
    code: Short?,
    said: CloseReason?,
): LobbyExit? =
    when (code) {
        CloseCodes.KICKED -> {
            if (said == CloseReason.VOTED_OUT) LobbyExit.VOTED_OUT else LobbyExit.KICKED
        }

        CloseCodes.LOBBY_GONE -> {
            LobbyExit.LOBBY_GONE
        }

        CloseCodes.REPLACED -> {
            LobbyExit.REPLACED
        }

        CloseCodes.SESSION_ENDED -> {
            LobbyExit.SESSION_ENDED
        }

        CloseCodes.UPGRADE_REQUIRED -> {
            LobbyExit.UPGRADE_REQUIRED
        }

        CloseCodes.SERVER_RESTARTING -> {
            LobbyExit.SERVER_RESTARTING
        }

        CloseCodes.NORMAL -> {
            when (said) {
                CloseReason.KICKED -> LobbyExit.KICKED

                CloseReason.VOTED_OUT -> LobbyExit.VOTED_OUT

                CloseReason.NOT_BACK -> LobbyExit.NOT_BACK

                CloseReason.LEFT -> LobbyExit.LEFT

                CloseReason.LOBBY_CLOSED, CloseReason.IDLE -> LobbyExit.LOBBY_CLOSED

                CloseReason.REPLACED -> LobbyExit.REPLACED

                CloseReason.SESSION_ENDED -> LobbyExit.SESSION_ENDED

                CloseReason.SERVER_RESTARTING -> LobbyExit.SERVER_RESTARTING

                // A normal close with nothing said: a server of a later build, or a proxy's. Join again.
                CloseReason.UNKNOWN, null -> null
            }
        }

        CloseCodes.INTERNAL -> {
            if (said == null) null else LobbyExit.LOBBY_CLOSED
        }

        // Everything else is worth another try: the ticket (4401), a timeout (4408), too much (4429), a
        // protocol slip (4400), a connection lost (none).
        else -> {
            null
        }
    }

internal fun PublicLobbyDto.toDomain(): PublicLobby =
    PublicLobby(code, hostName, hostAvatar, players, maxPlayers, inGame, settings.toDomain())

internal fun LobbyListDto.toDomain(): PublicLobbies =
    PublicLobbies(
        lobbies = lobbies.map { it.toDomain() },
        online = online,
        searching = searching,
    )
