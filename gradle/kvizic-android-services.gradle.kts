/*
 * The ids of the Google services an Android build of the game uses: Google Play Games Services' project
 * and the game server's OAuth client. Each is the Gradle property of its name
 * (`-Pkvizic.playgames.appId=...`, or `~/.gradle/gradle.properties`), or else the same name in the
 * repository's `local.properties`, which git ignores, as the analytics key is
 * (gradle/kvizic-analytics.gradle.kts). Neither is a secret, the game server's client secret staying on
 * the server, but neither is committed either: they are the developer's.
 *
 * - `kvizic.playgames.appId`: the Play Games Services project id, the digits the Play Console shows,
 *   which the manifest's `com.google.android.gms.games.APP_ID` names.
 * - `kvizic.playgames.serverClientId`: the game server credential's OAuth client id, the server's
 *   `PLAY_GAMES_CLIENT_ID`, which the app asks Play Games for a server auth code for.
 *
 * Without both, Play Games is off on that build, and the app says so in one log line at launch; every
 * test and CI build runs with it off. An id holding what it cannot hold fails the build here, since each
 * is written into generated code.
 *
 * Applied by `:app:androidApp`, which reads `extra["kvizicPlayGamesAppId"]` and
 * `extra["kvizicPlayGamesServerClientId"]`, each empty for none.
 */
import java.io.StringReader
import java.util.Properties

val localProperties =
    Properties().apply {
        providers
            .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
            .asText
            .orNull
            ?.let { text -> load(StringReader(text)) }
    }

fun serviceSetting(name: String): String =
    (providers.gradleProperty(name).orNull ?: localProperties.getProperty(name)).orEmpty().trim()

fun checked(
    name: String,
    allowed: Regex,
    what: String,
): String =
    serviceSetting(name).also { value ->
        require(value.isEmpty() || allowed.matches(value)) { "$name must be $what" }
    }

extra["kvizicPlayGamesAppId"] =
    checked("kvizic.playgames.appId", Regex("[0-9]+"), "digits: the Play Games project id")
extra["kvizicPlayGamesServerClientId"] =
    checked(
        "kvizic.playgames.serverClientId",
        Regex("[A-Za-z0-9._-]+"),
        "an OAuth client id: letters, digits, '.', '_' and '-'",
    )
