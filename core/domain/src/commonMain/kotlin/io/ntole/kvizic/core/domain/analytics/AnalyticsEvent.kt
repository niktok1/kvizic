package io.ntole.kvizic.core.domain.analytics

/**
 * The name of every event the game reports, in one place, so what is sent is listed here. A name never
 * changes once it is sent: a dashboard built on it would lose it. The properties each carries are
 * [AnalyticsProperty]'s.
 *
 * The game's own: a room entered and left, and a game played to its end.
 */
public object AnalyticsEvent {
    /** The app came to the foreground: at launch, and from the background ([AnalyticsProperty.FROM_BACKGROUND]). */
    public const val APP_OPENED: String = "app_opened"

    /** The app went to the background, after [AnalyticsProperty.DURATION_MS] in the foreground. */
    public const val APP_BACKGROUNDED: String = "app_backgrounded"

    /**
     * A screen was left, [AnalyticsProperty.SCREEN], after [AnalyticsProperty.DURATION_MS] on it: for
     * another screen, or for the background. A screen shown is the service's own `$screen`.
     */
    public const val SCREEN_LEFT: String = "screen_left"

    /** A button, a tile or anything else tapped, named by [AnalyticsProperty.ELEMENT]. */
    public const val TAP: String = "tap"

    /** The game showed the player a failure, [AnalyticsProperty.CODE], of [AnalyticsProperty.ACTION]. */
    public const val ERROR_SHOWN: String = "error_shown"

    /** The player picked a language to play in, [AnalyticsProperty.LANGUAGE]. */
    public const val LANGUAGE_CHANGED: String = "language_changed"

    /** The player turned the game's sound on or off, [AnalyticsProperty.ENABLED]. */
    public const val SOUND_CHANGED: String = "sound_changed"

    /**
     * The player signed in with Google Play Games Services: [AnalyticsProperty.AUTOMATIC] whether at
     * launch with no tap, and [AnalyticsProperty.SWITCHED] whether it made this device another player's.
     */
    public const val PLAY_GAMES_SIGNED_IN: String = "play_games_signed_in"

    /** The player deleted their account, and plays on as a fresh guest. */
    public const val ACCOUNT_DELETED: String = "account_deleted"

    /** The player took a seat in a room, [AnalyticsProperty.WAY]: `quick_play`, `solo`, `create` or `join`. */
    public const val ROOM_ENTERED: String = "room_entered"

    /** The player is out of the room they were in, for [AnalyticsProperty.EXIT], a `LobbyExit`'s name. */
    public const val ROOM_EXITED: String = "room_exited"

    /**
     * A game the player played was over: their [AnalyticsProperty.RANK] among [AnalyticsProperty.PLAYERS],
     * after [AnalyticsProperty.QUESTIONS], and [AnalyticsProperty.SOLO] whether alone.
     */
    public const val GAME_FINISHED: String = "game_finished"
}
