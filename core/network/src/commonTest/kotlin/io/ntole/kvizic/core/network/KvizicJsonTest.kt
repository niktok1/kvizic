package io.ntole.kvizic.core.network

import io.ntole.kvizic.core.error.ErrorCode
import io.ntole.kvizic.core.error.ErrorDto
import io.ntole.kvizic.core.player.NameSource
import io.ntole.kvizic.core.player.PlayerStatsDto
import io.ntole.kvizic.core.player.ProfileDto
import io.ntole.kvizic.core.topic.TopicListDto
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the client's half of the wire enum rule. Nothing else would notice it breaking: the failure only
 * shows on an installed client older than a server-side addition, never in a build where both sides are
 * current.
 */
class KvizicJsonTest {
    @Test
    fun `an error code this build has never heard of decodes as UNKNOWN`() {
        val error = KvizicJson.decodeFromString<ErrorDto>("""{"code":"CODE_FROM_THE_FUTURE","message":"m"}""")

        assertEquals(ErrorCode.UNKNOWN, error.code)
        assertEquals("m", error.message)
    }

    @Test
    fun `a name source this build has never heard of decodes as UNKNOWN`() {
        val profile =
            KvizicJson.decodeFromString<ProfileDto>(
                """{"playerId":"p1","displayName":"Брзи Јеж","nameSource":"SOURCE_FROM_THE_FUTURE","avatarId":"fox"}""",
            )

        assertEquals(NameSource.UNKNOWN, profile.nameSource)
        assertEquals("Брзи Јеж", profile.displayName)
    }

    @Test
    fun `a profile sent without its stats decodes with none`() {
        val profile =
            KvizicJson.decodeFromString<ProfileDto>(
                """{"playerId":"p1","displayName":"x","avatarId":"fox"}""",
            )

        assertEquals(PlayerStatsDto(), profile.stats)
        assertEquals(false, profile.playGamesLinked)
    }

    /** A stat added later decodes on this build, and one this build has is read whole. */
    @Test
    fun `a stat this build has never heard of is ignored beside those it has`() {
        val stats =
            KvizicJson.decodeFromString<PlayerStatsDto>(
                """{"gamesPlayed":4,"streakFromTheFuture":9,"bestTopicId":"MUSIC"}""",
            )

        assertEquals(PlayerStatsDto(gamesPlayed = 4, bestTopicId = "MUSIC"), stats)
    }

    /** Topics are server data, not an enum: a new one is only an id this build has no name for. */
    @Test
    fun `a topic this build has never heard of still decodes`() {
        val topics =
            KvizicJson.decodeFromString<TopicListDto>(
                """{"topics":[{"id":"TOPIC_FROM_THE_FUTURE","nameSr":"Ново","nameEn":"New"}]}""",
            )

        assertEquals("TOPIC_FROM_THE_FUTURE", topics.topics.single().id)
        assertEquals(0, topics.topics.single().questionCount)
    }

    /**
     * What a build from before a code was added makes of it, read through this build's `KvizicJson` into
     * the contract as that build had it: the codes it knew, with its `UNKNOWN` default. It reads the
     * failure as one it cannot name rather than failing the whole payload, which is what `UNKNOWN` is for.
     */
    @Test
    fun `a build from before a new error code reads it as UNKNOWN`() {
        val error = KvizicJson.decodeFromString<ErrorBefore>("""{"code":"SERVER_DRAINING","message":"m"}""")

        assertEquals(CodeBefore.UNKNOWN, error.code)
    }

    @Serializable
    private enum class CodeBefore { UNAUTHORIZED, LOBBY_FULL, UNKNOWN }

    @Serializable
    private data class ErrorBefore(
        val message: String? = null,
        val code: CodeBefore = CodeBefore.UNKNOWN,
    )

    @Test
    fun `a field this build has never heard of is ignored`() {
        val error = KvizicJson.decodeFromString<ErrorDto>("""{"code":"LOBBY_FULL","retryAfterSeconds":30}""")

        assertEquals(ErrorCode.LOBBY_FULL, error.code)
    }
}
