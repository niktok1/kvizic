package io.ntole.kvizic.server.player

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.slf4j.LoggerFactory

/**
 * Where a push is sent when a player opens the app, from `NOTIFY_NTFY_TOPIC` and `NOTIFY_EXCLUDE_PLAYER_IDS`:
 * the owner's own phone, through ntfy.sh. The topic is the only secret of the channel, so [toString] shows none
 * of it and nothing that prints the server's configuration can log it.
 */
class OpenedNotice(
    val topic: String,
    /** Players who never push, the owner's own accounts. */
    val excludedPlayerIds: Set<String> = emptySet(),
) {
    override fun toString(): String = "OpenedNotice(topic=***, excluded=${excludedPlayerIds.size})"
}

/** Told when a player begins a session, at a launch: a guest's first, a refresh, a Play Games sign-in. */
fun interface OpenedNotifier {
    /** Never blocks, never throws: a notice that fails is lost, and no request waits for it. */
    fun opened(playerId: String)

    companion object {
        /** Says nothing: the default, and every server with no `NOTIFY_NTFY_TOPIC`. */
        val None: OpenedNotifier = OpenedNotifier { }
    }
}

/**
 * Posts "a player opened the app" to ntfy.sh for every player but [OpenedNotice.excludedPlayerIds]: no name, no id,
 * nothing of the player leaves the server. Sent from [scope], so a request never waits for it.
 */
class NtfyOpenedNotifier(
    private val notice: OpenedNotice,
    private val client: HttpClient,
    private val scope: CoroutineScope,
    private val endpoint: String = ENDPOINT,
) : OpenedNotifier {
    override fun opened(playerId: String) {
        if (playerId in notice.excludedPlayerIds) return
        scope.launch {
            try {
                withTimeoutOrNull(SEND_TIMEOUT_MILLIS) {
                    client.post("$endpoint/${notice.topic}") {
                        header("Title", "Kvizic")
                        setBody("Player opened the app")
                    }
                } ?: log.warn("The push for an opened app timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                // The class alone: a message may carry the request's address, which holds the topic.
                log.warn("The push for an opened app failed: {}", e::class.simpleName)
            }
        }
    }

    private companion object {
        const val ENDPOINT = "https://ntfy.sh"
        const val SEND_TIMEOUT_MILLIS = 5_000L
        val log = LoggerFactory.getLogger(NtfyOpenedNotifier::class.java)
    }
}
