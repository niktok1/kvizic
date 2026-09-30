package io.ntole.kvizic.services

/**
 * The ids of the Google services a build uses, from its build config (gradle/kvizic-android-services.gradle.kts):
 * Google Play Games Services' project and the game server's OAuth client. Neither is a secret, and neither
 * is committed. Play Games missing either id is off on the build, which [offLines] says, one line, and
 * which is how every test and CI build runs.
 *
 * Common code, though only Android has Play Games, so what turns it on is held to one rule and tested once.
 */
data class GoogleServiceSettings(
    /** The Play Games Services project id, which the Android manifest's `APP_ID` names too. */
    val playGamesAppId: String = "",
    /** The game server credential's OAuth client id, the server's `PLAY_GAMES_CLIENT_ID`. */
    val playGamesServerClientId: String = "",
) {
    /** Whether Play Games is on: both its ids are there. */
    val playGamesOn: Boolean
        get() = playGamesMissing.isEmpty()

    private val playGamesMissing: List<String>
        get() =
            listOfNotNull(
                "kvizic.playgames.appId".takeIf { playGamesAppId.isBlank() },
                "kvizic.playgames.serverClientId".takeIf { playGamesServerClientId.isBlank() },
            )

    /** One line for each service that is off on this build, naming the ids it lacks; none when all are on. */
    fun offLines(): List<String> =
        listOfNotNull(
            playGamesMissing
                .takeIf { it.isNotEmpty() }
                ?.let { "Play Games is off on this build: no ${it.joinToString()}" },
        )
}
