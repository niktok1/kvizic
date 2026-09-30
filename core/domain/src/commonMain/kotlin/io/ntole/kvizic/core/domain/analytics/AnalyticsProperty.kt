package io.ntole.kvizic.core.domain.analytics

/** The name of every property an [AnalyticsEvent] carries of its own. */
public object AnalyticsProperty {
    /** A screen's name, as the navigator keeps it: `home`, `about` or `update`. */
    public const val SCREEN: String = "screen"

    /** How long something lasted, in whole milliseconds. */
    public const val DURATION_MS: String = "duration_ms"

    /** What was tapped: a stable name, never the text it shows, which changes with the language. */
    public const val ELEMENT: String = "element"

    /** A failure's code, the domain's name for it (`NETWORK`, `LOBBY_FULL`...). */
    public const val CODE: String = "code"

    /** What failed: `profile` or `delete_account`, and the game's own as they come. */
    public const val ACTION: String = "action"

    /** Whether the app came from the background rather than being launched. */
    public const val FROM_BACKGROUND: String = "from_background"

    /** Whether something happened by itself, with no tap: a Play Games sign-in at launch. */
    public const val AUTOMATIC: String = "automatic"

    /** Whether a sign-in made this device another player's, the one before left behind. */
    public const val SWITCHED: String = "switched"

    /** A language's tag: `sr-Cyrl`, `sr-Latn` or `en`. */
    public const val LANGUAGE: String = "language"
}
