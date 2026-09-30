package io.ntole.kvizic.server.config

import io.ntole.kvizic.server.lobby.GameTimings
import io.ntole.kvizic.server.lobby.LobbyLimits
import io.ntole.kvizic.server.rules.ScoringRules

/** The game's own settings: how long everything takes, how much the server holds, and how answers score. */
data class GameConfig(
    val timings: GameTimings = GameTimings.DEFAULT,
    val limits: LobbyLimits = LobbyLimits.DEFAULT,
    val scoring: ScoringRules = ScoringRules.DEFAULT,
)
