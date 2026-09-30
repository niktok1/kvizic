package io.ntole.kvizic.core.network

import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * One storage holding every environment's session, as desktop, iOS and web have: a build for one server
 * must neither see nor replace the session another server issued.
 */
class SessionStoreTest {
    private val storage = InMemoryTokenStorage()

    @Test
    fun `each environment keeps its own session in one storage`() =
        runTest {
            val stores = KvizicEnvironment.entries.associateWith { SessionStore(storage, it) }
            stores.forEach { (environment, store) -> store.write(session(environment.name)) }

            stores.forEach { (environment, store) ->
                assertEquals(environment.name, store.read()?.playerId, environment.name)
            }
        }

    @Test
    fun `clearing one environment's session leaves the others'`() =
        runTest {
            val dev = SessionStore(storage, KvizicEnvironment.DEV)
            val prod = SessionStore(storage, KvizicEnvironment.PROD)
            dev.write(session("dev"))
            prod.write(session("prod"))

            dev.clear()

            assertNull(dev.read())
            assertEquals("prod", prod.read()?.playerId)
        }

    /** One prefix for every key the game keeps, and each environment's name after it, prod's too. */
    @Test
    fun `each environment's session is under kvizic session and its name`() =
        runTest {
            mapOf(
                KvizicEnvironment.LOCAL to "kvizic.session.local",
                KvizicEnvironment.DEV to "kvizic.session.dev",
                KvizicEnvironment.PROD to "kvizic.session.prod",
            ).forEach { (environment, key) ->
                SessionStore(storage, environment).write(session(environment.name))

                assertNotNull(storage.read(key), key)
            }
            assertNull(storage.read("kvizic.session"), "no key without its environment")
        }

    @Test
    fun `a session that is not one reads as none`() =
        runTest {
            storage.write("kvizic.session.dev", "not a session")

            assertNull(SessionStore(storage, KvizicEnvironment.DEV).read())
        }
}
