package io.ntole.kvizic.core.network.realtime

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.network.ClientBuild
import io.ntole.kvizic.core.protocol.ClientMessage
import io.ntole.kvizic.core.protocol.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The hello a socket opens with, which names the build and the language the game is shown in. Built apart from
 * the socket, which no mock engine opens; `:e2e` sends it over a real one.
 */
class KtorPlayTransportTest {
    @Test
    fun `the hello names the build and the language shown when the socket opens`() {
        var language = "hr"
        val client = KtorPlayTransport.realtimeClient(MockEngine { respondOk() })
        val transport =
            KtorPlayTransport(API, ClientBuild(KvizicApi.ClientPlatform.ANDROID, 10203), client) { language }
        try {
            assertEquals(
                ClientMessage.Hello(
                    ticket = "t1",
                    protocol = Protocol.VERSION,
                    platform = KvizicApi.ClientPlatform.ANDROID,
                    build = 10203,
                    language = "hr",
                ),
                transport.hello("t1"),
            )

            // A language changed is the next socket's, a reconnection's too.
            language = "sr-Latn"
            assertEquals("sr-Latn", transport.hello("t2").language)
        } finally {
            client.close()
        }
    }

    /** A transport told no language, as a test's or an old caller's, names none, which reads Serbian Cyrillic. */
    @Test
    fun `a transport told no language names none`() {
        val client = KtorPlayTransport.realtimeClient(MockEngine { respondOk() })
        try {
            assertEquals("", KtorPlayTransport(API, build = null, client).hello("t1").language)
        } finally {
            client.close()
        }
    }

    private companion object {
        const val API = "https://kvizic.test"
    }
}
