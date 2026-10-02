package io.ntole.kvizic.core.lobby

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.question.Difficulty
import kotlinx.serialization.Serializable

/**
 * What a host configures. [topics] empty is Све, every topic, the default and main mode. The values must
 * be on [KvizicApi.Limits]'s lists; anything else is refused as `INVALID_SETTINGS`.
 */
@Serializable
public data class LobbySettingsDto(
    public val questionCount: Int = DEFAULT_QUESTION_COUNT,
    public val secondsPerQuestion: Int = DEFAULT_SECONDS,
    public val topics: List<String> = emptyList(),
    public val maxPlayers: Int = KvizicApi.Limits.MAX_PLAYERS,
    public val visibility: Visibility = Visibility.PRIVATE,
    /** Whether a wrong answer costs points (the minus that makes random fast clicking a loss). */
    public val wrongAnswerPenalty: Boolean = true,
    /** The level most of a game's questions are at, the others for the rest: a mix, never a filter. */
    public val difficulty: Difficulty = Difficulty.MEDIUM,
    /**
     * What the room is called, if the host named it: at most [KvizicApi.Limits.MAX_ROOM_NAME_LENGTH] code points,
     * cleaned as a player's name is. Null for a room with no name of its own, which the list calls by its host.
     */
    public val name: String? = null,
) {
    public companion object {
        public const val DEFAULT_QUESTION_COUNT: Int = 10
        public const val DEFAULT_SECONDS: Int = 15

        /** A solo run's fixed format, so personal bests compare. */
        public val SOLO: LobbySettingsDto = LobbySettingsDto(maxPlayers = 1, visibility = Visibility.PRIVATE)

        /** What Quick play opens when it finds no lobby to join. */
        public val QUICK_PLAY: LobbySettingsDto = LobbySettingsDto(visibility = Visibility.PUBLIC)
    }
}

/** Who can find a lobby: only by its code or link, or also in the public list and Quick play. */
@Serializable
public enum class Visibility {
    PRIVATE,
    PUBLIC,
    UNKNOWN,
}

/** A lobby's kind, as its views show it. */
@Serializable
public enum class LobbyKind {
    PRIVATE,
    PUBLIC,

    /** A solo run: one seat, never listed, the fixed format. */
    SOLO,

    UNKNOWN,
}
