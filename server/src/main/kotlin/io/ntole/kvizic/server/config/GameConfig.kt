package io.ntole.kvizic.server.config

import io.ntole.kvizic.server.lobby.GameTimings
import io.ntole.kvizic.server.lobby.LobbyLimits
import io.ntole.kvizic.server.rules.CoinRules
import io.ntole.kvizic.server.rules.ScoringRules

/**
 * The game's own settings: how long everything takes, how much the server holds, how answers score and what
 * a game pays in coins.
 */
data class GameConfig(
    val timings: GameTimings = GameTimings.DEFAULT,
    val limits: LobbyLimits = LobbyLimits.DEFAULT,
    val scoring: ScoringRules = ScoringRules.DEFAULT,
    val coins: CoinRules = CoinRules.DEFAULT,
)
