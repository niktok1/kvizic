package io.ntole.kvizic.core.data.lobby

import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.GameResults
import io.ntole.kvizic.core.domain.lobby.Lobby
import io.ntole.kvizic.core.domain.lobby.LobbyKind
import io.ntole.kvizic.core.domain.lobby.LobbyMember
import io.ntole.kvizic.core.domain.lobby.LobbyVisibility
import io.ntole.kvizic.core.domain.lobby.Standing
import io.ntole.kvizic.core.protocol.ServerMessage
import kotlin.time.ComparableTimeMark

/** The game under way, as the reducer keeps it between its messages. */
internal data class GameContext(
    val gameId: String,
    val players: List<String>,
    val standings: List<Standing>,
)

/** A lobby as this device knows it, at [version]: the last state change it applied. */
internal data class LobbyModel(
    val you: String,
    val version: Long,
    val lobby: Lobby,
    val phase: GamePhase,
    val game: GameContext?,
)

/** What a state change did to the model. */
internal sealed interface Step {
    data class Changed(
        val model: LobbyModel,
    ) : Step

    /** One or more versions are missing, or the change needs what the model lacks: ask for a snapshot. */
    data object OutOfOrder : Step

    /** Seen already: a change a snapshot had in it. */
    data object Stale : Step
}

/**
 * The lobby's state from its snapshot and every state change after it, one version at a time. Pure:
 * every deadline is anchored at `at`, when its message arrived. A change it cannot place, a version
 * skipped or a phase it did not see begin, is [Step.OutOfOrder], and the session asks for a snapshot.
 */
internal object LobbyReducer {
    fun snapshot(
        message: ServerMessage.Snapshot,
        at: ComparableTimeMark,
    ): LobbyModel {
        val lobby = message.lobby.toDomain()
        val phase = message.phase.toDomain(at, lobby.settings) ?: GamePhase.Waiting(lastResults = null)
        return LobbyModel(
            you = message.you,
            version = message.v,
            lobby = lobby,
            phase = phase,
            game = gameOf(phase),
        )
    }

    fun apply(
        model: LobbyModel,
        change: ServerMessage.StateChange,
        at: ComparableTimeMark,
    ): Step {
        if (change.v <= model.version) return Step.Stale
        if (change.v != model.version + 1) return Step.OutOfOrder
        val next = next(model, change, at) ?: return Step.OutOfOrder
        return Step.Changed(next.copy(version = change.v))
    }

    private fun next(
        model: LobbyModel,
        change: ServerMessage.StateChange,
        at: ComparableTimeMark,
    ): LobbyModel? {
        val lobby = model.lobby
        return when (change) {
            is ServerMessage.MemberJoined -> {
                model.copy(lobby = lobby.withMember(change.member.toDomain()))
            }

            is ServerMessage.MemberUpdated -> {
                model.copy(lobby = lobby.withMember(change.member.toDomain()))
            }

            is ServerMessage.MemberLeft -> {
                model.copy(lobby = lobby.copy(members = lobby.members.filterNot { it.playerId == change.player }))
            }

            is ServerMessage.HostChanged -> {
                model.copy(lobby = lobby.copy(host = change.host))
            }

            is ServerMessage.SettingsChanged -> {
                val settings = change.settings.toDomain()
                val kind =
                    when {
                        lobby.kind == LobbyKind.SOLO -> LobbyKind.SOLO
                        settings.visibility == LobbyVisibility.PUBLIC -> LobbyKind.PUBLIC
                        else -> LobbyKind.PRIVATE
                    }
                model.copy(lobby = lobby.copy(settings = settings, kind = kind))
            }

            is ServerMessage.CountdownStarted -> {
                model.copy(phase = GamePhase.Countdown(deadline(at, change.remainingMs), lastResultsOf(model.phase)))
            }

            is ServerMessage.GameStarted -> {
                val players = change.players.toSet()
                model.copy(
                    lobby =
                        lobby.mapMembers {
                            it.copy(
                                playing = it.playerId in players,
                                onResults =
                                    it.onResults && it.playerId !in players,
                            )
                        },
                    game =
                        GameContext(
                            change.gameId,
                            change.players,
                            change.players.map {
                                Standing(it, score = 0, rank = 1, correct = 0)
                            },
                        ),
                )
            }

            is ServerMessage.QuestionShown -> {
                val game = model.game ?: return null
                model.copy(
                    phase =
                        GamePhase.Reading(
                            game.gameId,
                            game.players,
                            change.question.toDomain(lobby.settings),
                            deadline(at, change.readMs),
                            game.standings,
                        ),
                )
            }

            is ServerMessage.AnswersOpened -> {
                val reading = model.phase as? GamePhase.Reading ?: return null
                if (reading.question.index != change.index) return null
                model.copy(
                    phase =
                        GamePhase.Answering(
                            gameId = reading.gameId,
                            players = reading.players,
                            question = reading.question,
                            options = change.options,
                            deadline = deadline(at, change.remainingMs, change.durationMs),
                            answered = emptySet(),
                            myPick = null,
                            picks = emptyMap(),
                            standings = reading.standings,
                        ),
                )
            }

            is ServerMessage.Picks -> {
                val answering = model.phase as? GamePhase.Answering ?: return null
                if (answering.question.index != change.index) return null
                val picks = change.picks.associate { it.player to it.option }
                model.copy(
                    phase =
                        answering.copy(
                            picks = picks,
                            answered = answering.answered + picks.keys,
                            myPick =
                                picks[model.you] ?: answering.myPick,
                        ),
                )
            }

            is ServerMessage.Progress -> {
                val answering = model.phase as? GamePhase.Answering ?: return null
                if (answering.question.index != change.index) return null
                model.copy(phase = answering.copy(answered = change.answered.toSet()))
            }

            is ServerMessage.Revealed -> {
                val game = model.game ?: return null
                val reveal = change.reveal.toDomain()
                model.copy(
                    phase =
                        GamePhase.Revealing(
                            game.gameId,
                            game.players,
                            reveal,
                            deadline(at, change.reveal.nextInMs),
                        ),
                    game = game.copy(standings = reveal.standings),
                )
            }

            is ServerMessage.GameOver -> {
                val results = change.results.toDomain()
                val players =
                    model.game
                        ?.players
                        .orEmpty()
                        .toSet() + results.standings.map { it.playerId }
                model.copy(
                    lobby =
                        lobby.mapMembers {
                            it.copy(
                                playing = false,
                                onResults =
                                    it.onResults || it.playerId in players,
                            )
                        },
                    phase = GamePhase.Waiting(results),
                    game = null,
                )
            }

            is ServerMessage.GameAborted -> {
                model.copy(
                    lobby = lobby.mapMembers { it.copy(playing = false) },
                    phase = GamePhase.Waiting(lastResultsOf(model.phase)),
                    game = null,
                )
            }
        }
    }

    private fun gameOf(phase: GamePhase): GameContext? =
        when (phase) {
            is GamePhase.Reading -> GameContext(phase.gameId, phase.players, phase.standings)
            is GamePhase.Answering -> GameContext(phase.gameId, phase.players, phase.standings)
            is GamePhase.Revealing -> GameContext(phase.gameId, phase.players, phase.reveal.standings)
            is GamePhase.Waiting, is GamePhase.Countdown -> null
        }

    private fun lastResultsOf(phase: GamePhase): GameResults? =
        when (phase) {
            is GamePhase.Waiting -> phase.lastResults
            is GamePhase.Countdown -> phase.lastResults
            else -> null
        }

    private fun Lobby.withMember(member: LobbyMember): Lobby =
        copy(members = (members.filterNot { it.playerId == member.playerId } + member).sortedBy { it.seat })

    private fun Lobby.mapMembers(change: (LobbyMember) -> LobbyMember): Lobby = copy(members = members.map(change))
}
