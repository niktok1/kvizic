package io.ntole.kvizic.region

import io.ntole.kvizic.core.domain.region.Country
import io.ntole.kvizic.core.network.InMemoryTokenStorage
import io.ntole.kvizic.core.network.TokenStorage
import io.ntole.kvizic.language.Language
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Where the player plays from and what they read, as a launch opens with them and the device keeps them. */
@OptIn(ExperimentalCoroutinesApi::class)
class RegionStoreTest {
    @Test
    fun `a first launch takes the country and the language from the device and keeps both`() =
        runTest {
            val storage = InMemoryTokenStorage()
            val device = AskedDevice(RegionHints("hr", "hr", "HR", listOf("hr-HR", "en")))

            val store = store(storage, device, installedBefore = false, offered = RELEASE)
            runCurrent()

            assertEquals(Country.HR to Language.CROATIAN, store.country.value to store.language.value)
            assertEquals<Pair<String?, String?>>("HR" to "hr", storage.read(COUNTRY) to storage.read(LANGUAGE))
            assertEquals(1, device.asked)
            assertNull(storage.read(WANTED), "nothing is waited for")
            // The server is told the same.
            assertEquals(Country.HR to "hr", store.currentCountry() to store.currentLanguageTag())
        }

    /**
     * A first launch before Croatian is offered shows Serbian in Latin and keeps it, waiting for Croatian: the
     * first launch of a build that offers it switches, once, and asks the device nothing to.
     */
    @Test
    fun `a first launch waits for its language and takes it once it is offered`() =
        runTest {
            val storage = InMemoryTokenStorage()
            val device = AskedDevice(RegionHints("hr", "hr", "HR", listOf("hr-HR", "en")))

            val first = store(storage, device, installedBefore = false, offered = SERBIAN_ONLY)
            runCurrent()
            assertEquals(Language.SERBIAN_LATIN, first.language.value)
            assertEquals<Pair<String?, String?>>("sr-Latn" to "hr", storage.read(LANGUAGE) to storage.read(WANTED))

            // A session is kept by now, as after any first game.
            val stillWaiting = store(storage, device, installedBefore = true, offered = SERBIAN_ONLY)
            runCurrent()
            assertEquals(Language.SERBIAN_LATIN, stillWaiting.language.value)
            assertEquals("hr", storage.read(WANTED))

            val offeredNow = store(storage, device, installedBefore = true, offered = RELEASE)
            runCurrent()
            assertEquals(Language.CROATIAN, offeredNow.language.value)
            assertEquals(Country.HR to "hr", offeredNow.currentCountry() to offeredNow.currentLanguageTag())
            assertEquals<Pair<String?, String?>>("hr" to null, storage.read(LANGUAGE) to storage.read(WANTED))

            val later = store(storage, device, installedBefore = true, offered = RELEASE)
            assertEquals(Language.CROATIAN, later.language.value)
            assertEquals(1, device.asked, "only the first launch asks the device")
        }

    /** The stand-in the player picks over is gone for good: what they pick is theirs, whatever comes after. */
    @Test
    fun `a language picked while another is waited for is the player's`() =
        runTest {
            val storage = InMemoryTokenStorage()
            val device = AskedDevice(RegionHints("mk", "mk", "MK", listOf("mk-MK")))
            val first = store(storage, device, installedBefore = false, offered = SERBIAN_ONLY)
            runCurrent()
            assertEquals(Language.SERBIAN_CYRILLIC, first.language.value)
            assertEquals("mk", storage.read(WANTED))

            first.selectLanguage(Language.SERBIAN_LATIN)
            runCurrent()
            assertNull(storage.read(WANTED))

            val offeredNow = store(storage, device, installedBefore = true, offered = RELEASE)
            assertEquals(Language.SERBIAN_LATIN, offeredNow.language.value)
        }

    /** A device in a language the game does not have takes its country's: Montenegro's Serbian in Latin. */
    @Test
    fun `a first launch in another language takes the country's`() =
        runTest {
            val storage = InMemoryTokenStorage()
            val device = AskedDevice(RegionHints("me", "me", "ME", listOf("en-ME")))

            val store = store(storage, device, installedBefore = false, offered = RELEASE)
            runCurrent()

            assertEquals(Country.ME to Language.SERBIAN_LATIN, store.country.value to store.language.value)
            assertEquals<Pair<String?, String?>>("ME" to "sr-Latn", storage.read(COUNTRY) to storage.read(LANGUAGE))
        }

    /** An install that picked its language before countries were kept keeps it, and has its country found once. */
    @Test
    fun `an install from before keeps its language and has its country detected once`() =
        runTest {
            val storage = InMemoryTokenStorage()
            storage.write(LANGUAGE, "sr-Latn")
            val device = AskedDevice(RegionHints("si", "si", "SI", listOf("sl-SI")))

            val store = store(storage, device, installedBefore = true, offered = RELEASE)
            runCurrent()

            assertEquals(Country.SI to Language.SERBIAN_LATIN, store.country.value to store.language.value)
            assertEquals<Pair<String?, String?>>("SI" to "sr-Latn", storage.read(COUNTRY) to storage.read(LANGUAGE))

            val again = store(storage, device, installedBefore = true, offered = RELEASE)
            assertEquals(Country.SI to Language.SERBIAN_LATIN, again.country.value to again.language.value)
            assertEquals(1, device.asked, "the country is detected once")
        }

    /**
     * An install from before that never picked a language kept none, and has always shown Serbian Cyrillic: its
     * session tells it from a first launch, and it shows what it showed, whatever the device's language.
     */
    @Test
    fun `an install from before that never picked a language keeps Serbian Cyrillic`() =
        runTest {
            val storage = InMemoryTokenStorage()
            val device = AskedDevice(RegionHints("rs", "rs", "RS", listOf("sr-Latn-RS")))

            val store = store(storage, device, installedBefore = true, offered = RELEASE)
            runCurrent()

            assertEquals(Country.RS to Language.SERBIAN_CYRILLIC, store.country.value to store.language.value)
            assertEquals("RS", storage.read(COUNTRY))
            assertNull(storage.read(LANGUAGE), "nothing the player did not pick is written")
        }

    @Test
    fun `a later launch opens with what is kept and asks the device nothing`() =
        runTest {
            val storage = InMemoryTokenStorage()
            storage.write(COUNTRY, "MK")
            storage.write(LANGUAGE, "mk")
            val device = AskedDevice(RegionHints("rs", "rs", "RS", listOf("sr")))

            val store = store(storage, device, installedBefore = true)

            assertEquals(Country.MK to Language.MACEDONIAN, store.country.value to store.language.value)
            assertEquals(0, device.asked)
        }

    /** A newer build's country is one no question is scoped to; a newer build's language is the default. */
    @Test
    fun `what this build does not know opens as UNKNOWN and the default`() =
        runTest {
            val storage = InMemoryTokenStorage()
            storage.write(COUNTRY, "XK")
            storage.write(LANGUAGE, "de")

            val store = store(storage, AskedDevice(RegionHints()), installedBefore = true)

            assertEquals(Country.UNKNOWN to Language.DEFAULT, store.country.value to store.language.value)
        }

    /** A phone's services may fail where they should say nothing: the launch goes on, from Serbia. */
    @Test
    fun `a device that fails to say plays from Serbia`() =
        runTest {
            val failing = DeviceRegion { throw IllegalStateException("no telephony") }

            val store = store(InMemoryTokenStorage(), failing, installedBefore = false, offered = RELEASE)

            assertEquals(Country.RS to Language.SERBIAN_CYRILLIC, store.country.value to store.language.value)
        }

    @Test
    fun `a pick shows at once and is the next launch's`() =
        runTest {
            val storage = InMemoryTokenStorage()
            val store = store(storage, AskedDevice(RegionHints("rs")), installedBefore = true)

            store.selectCountry(Country.BA)
            store.selectLanguage(Language.SERBIAN_LATIN)

            assertEquals(Country.BA to Language.SERBIAN_LATIN, store.country.value to store.language.value)
            assertEquals(Country.BA to "sr-Latn", store.currentCountry() to store.currentLanguageTag())
            runCurrent()
            val next = store(storage, AskedDevice(RegionHints("rs")), installedBefore = true)
            assertEquals(Country.BA to Language.SERBIAN_LATIN, next.country.value to next.language.value)
        }

    /**
     * A pick made while the last one's write is still on its way waits for it, and lands after it: were the two
     * written at once, the earlier, landing last, would be the one kept.
     */
    @Test
    fun `a pick made while the last is being written is the one kept`() =
        runTest {
            val storage = SlowStorage(COUNTRY to "RS", LANGUAGE to "sr-Cyrl")
            val store = store(storage, AskedDevice(RegionHints("rs")), installedBefore = true)

            store.selectCountry(Country.HR)
            runCurrent()
            store.selectCountry(Country.SI)
            runCurrent()
            // The later write lands first, as a disk may land them, then the earlier.
            storage.landing(1).complete(Unit)
            runCurrent()
            storage.landing(0).complete(Unit)
            runCurrent()

            assertEquals("SI", storage.read(COUNTRY))
            assertEquals(listOf(COUNTRY to "HR", COUNTRY to "SI"), storage.writing)
        }

    /** A slow disk holds up neither the screen nor the other setting. */
    @Test
    fun `a write stuck on a slow disk holds up nothing`() =
        runTest {
            val storage = SlowStorage()
            val store = store(storage, AskedDevice(RegionHints("rs")), installedBefore = false, offered = RELEASE)

            store.selectLanguage(Language.MACEDONIAN)
            runCurrent()

            assertEquals(Language.MACEDONIAN, store.language.value)
            assertEquals(setOf(COUNTRY, LANGUAGE), storage.writing.map { it.first }.toSet())
        }

    @Test
    fun `a pick that cannot be kept is still this run's`() =
        runTest {
            val store = store(FailingStorage(), AskedDevice(RegionHints("rs")), installedBefore = true)

            store.selectCountry(Country.SI)
            runCurrent()

            assertEquals(Country.SI, store.country.value)
        }

    private fun TestScope.store(
        storage: TokenStorage,
        device: DeviceRegion,
        installedBefore: Boolean,
        offered: List<Language> = Language.OFFERED,
    ) = RegionStore(storage, device, installedBefore = { installedBefore }, scope = backgroundScope, offered = offered)

    /** A device saying [hints], counting how often it is asked. */
    private class AskedDevice(
        private val hints: RegionHints,
    ) : DeviceRegion {
        var asked = 0

        override fun hints(): RegionHints {
            asked++
            return hints
        }
    }

    /**
     * A storage holding [kept], whose writes wait as a slow disk's do: the [writing] begun, in the order begun, the
     * n-th of them landing once [landing] (n) is completed.
     */
    private class SlowStorage(
        vararg kept: Pair<String, String>,
    ) : TokenStorage {
        val writing = mutableListOf<Pair<String, String>>()
        private val landings = mutableListOf<CompletableDeferred<Unit>>()
        private val values = mutableMapOf(*kept)

        fun landing(write: Int): CompletableDeferred<Unit> {
            while (landings.size <= write) landings += CompletableDeferred<Unit>()
            return landings[write]
        }

        override fun read(key: String): String? = values[key]

        override suspend fun write(
            key: String,
            value: String,
        ) {
            val landed = landing(writing.size)
            writing += key to value
            landed.await()
            values[key] = value
        }

        override suspend fun remove(key: String) {
            values.remove(key)
        }
    }

    /** A storage whose every write fails, as a full disk's does. */
    private class FailingStorage : TokenStorage {
        override fun read(key: String): String? = null

        override suspend fun write(
            key: String,
            value: String,
        ): Unit = throw IllegalStateException("the disk is full")

        override suspend fun remove(key: String) = Unit
    }

    private companion object {
        const val COUNTRY = RegionStore.COUNTRY_KEY
        const val LANGUAGE = RegionStore.LANGUAGE_KEY
        const val WANTED = RegionStore.WANTED_KEY

        /** What is offered until the four are reviewed. */
        val SERBIAN_ONLY = listOf(Language.SERBIAN_CYRILLIC, Language.SERBIAN_LATIN)

        val RELEASE =
            listOf(
                Language.SERBIAN_CYRILLIC,
                Language.SERBIAN_LATIN,
                Language.CROATIAN,
                Language.BOSNIAN,
                Language.SLOVENIAN,
                Language.MACEDONIAN,
            )
    }
}
