package io.ntole.kvizic.region

import io.ntole.kvizic.core.domain.region.Country
import io.ntole.kvizic.core.domain.region.PlayerRegion
import io.ntole.kvizic.core.network.TokenStorage
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.LanguageViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Where the player plays from and the language the game is shown in, as the device keeps them: the one copy of
 * both, for the app's life, which the Settings screen changes ([CountryViewModel], [LanguageViewModel]) and the
 * data layer tells the server of ([PlayerRegion]): the country on every request, the language in every hello.
 *
 * Kept in [storage], the platform's key-value storage the sessions are kept in, under [COUNTRY_KEY] and
 * [LANGUAGE_KEY], one key each for the device whatever server the build talks to: they are the player's, not the
 * environment's. A change shows at once and is kept in [scope]; a write that fails leaves it this run's only,
 * the next launch opening with the one kept before, which is no reason to stop the game or to say anything.
 *
 * What a launch opens with:
 * - A **first launch**, nothing kept and no session either, detects the country from what [device] says
 *   ([CountryDetection]) and picks the language from the device's own, else the country's ([FirstLaunch], among
 *   [offered]), and keeps both. When the one it wants ([FirstLaunch.wanted]) is not offered yet, it shows and
 *   keeps the one [FirstLaunch] gives in its stead, Serbian in that one's script, and keeps the one it wants
 *   under [WANTED_KEY]: the first launch of a build that offers it switches to it, once, unless the player has
 *   picked a language meanwhile. Kept as the player's own, a stand-in would hold every install made before its
 *   language came in Serbian for good.
 * - An **install from before** keeps its language: the one it kept, or Serbian Cyrillic, which it has always
 *   shown when it kept none (a session kept is what tells it from a first launch, since an install kept its
 *   language only once one was picked). It only has its country detected, once, and kept.
 * - Every later launch opens with what is kept. A kept country this build does not know is UNKNOWN, which the
 *   server asks only the common questions of, and a kept language it does not know is [Language.DEFAULT].
 *
 * [installedBefore] says whether a session is kept, asked only when neither is.
 */
class RegionStore(
    private val storage: TokenStorage,
    device: DeviceRegion,
    installedBefore: () -> Boolean,
    private val scope: CoroutineScope,
    offered: List<Language> = Language.OFFERED,
) : PlayerRegion {
    private val countryNow: MutableStateFlow<Country>
    private val languageNow: MutableStateFlow<Language>

    /** The language a first launch wanted and found not offered yet, until it is or the player picks one. */
    private val waitingFor: MutableStateFlow<Language?>

    /** One lock a key, so writes of one key land in the order asked, and one stuck holds up no other. */
    private val writing = mapOf(COUNTRY_KEY to Mutex(), LANGUAGE_KEY to Mutex(), WANTED_KEY to Mutex())

    init {
        val keptCountry = storage.read(COUNTRY_KEY)
        val keptLanguage = storage.read(LANGUAGE_KEY)
        val keptWanted = storage.read(WANTED_KEY)
        // Read once at most, and only when something is to be detected.
        val hints by lazy { device.hintsOrNone() }
        val firstLaunch = keptCountry == null && keptLanguage == null && !installedBefore()
        countryNow = MutableStateFlow(keptCountry?.let(::countryNamed) ?: CountryDetection.detect(hints))
        // What a first launch wants, or what one wanted and this build may offer now.
        val wanted =
            if (firstLaunch) {
                FirstLaunch.wanted(hints, countryNow.value)
            } else {
                Language.entries.firstOrNull { it.tag == keptWanted }
            }
        val arrived = wanted != null && wanted in offered
        languageNow =
            MutableStateFlow(
                when {
                    wanted != null && arrived -> wanted
                    keptLanguage != null -> Language.ofTag(keptLanguage)
                    firstLaunch -> FirstLaunch.language(hints, countryNow.value, offered)
                    else -> Language.DEFAULT
                },
            )
        waitingFor = MutableStateFlow(wanted.takeUnless { arrived })
        if (keptCountry == null) keep(COUNTRY_KEY) { countryNow.value.name }
        if (firstLaunch || arrived) keep(LANGUAGE_KEY) { languageNow.value.tag }
        if (waitingFor.value?.tag != keptWanted) keep(WANTED_KEY) { waitingFor.value?.tag }
    }

    /** Where the player plays from. */
    val country: StateFlow<Country> = countryNow.asStateFlow()

    /** The language the game is shown in. */
    val language: StateFlow<Language> = languageNow.asStateFlow()

    /** Plays from [country] from now on: the next request names it. */
    fun selectCountry(country: Country) {
        if (country == countryNow.value) return
        countryNow.value = country
        keep(COUNTRY_KEY) { countryNow.value.name }
    }

    /** Shows the game in [language] from now on: the next socket's hello names it. The player's, so kept as theirs. */
    fun selectLanguage(language: Language) {
        if (language == languageNow.value) return
        languageNow.value = language
        keep(LANGUAGE_KEY) { languageNow.value.tag }
        if (waitingFor.value != null) {
            waitingFor.value = null
            keep(WANTED_KEY) { waitingFor.value?.tag }
        }
    }

    override fun currentCountry(): Country = countryNow.value

    override fun currentLanguageTag(): String = languageNow.value.tag

    /**
     * Writes under [key] what [value] is when the write's turn comes, so the last change asked is what stays; a
     * value of none removes the key.
     */
    private fun keep(
        key: String,
        value: () -> String?,
    ) {
        scope.launch {
            try {
                writing.getValue(key).withLock {
                    val now = value()
                    if (now != null) storage.write(key, now) else storage.remove(key)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ignored: Exception) {
                // This run's only, as the KDoc says.
            }
        }
    }

    companion object {
        /** Beside the sessions' keys (`SessionStore.keyFor`), and none of them. */
        const val COUNTRY_KEY: String = "kvizic.country"

        /** Beside the sessions' keys, and none of them: the key the language has always been kept under. */
        const val LANGUAGE_KEY: String = "kvizic.language"

        /** The language a first launch wanted and this build did not offer yet, by its tag, kept until it does. */
        const val WANTED_KEY: String = "kvizic.language.wanted"

        /** The country [name] names, UNKNOWN for one this build does not know. */
        private fun countryNamed(name: String): Country =
            Country.entries.firstOrNull { it.name == name } ?: Country.UNKNOWN

        /** What [DeviceRegion.hints] says, or nothing when the device fails to say: the launch goes on. */
        private fun DeviceRegion.hintsOrNone(): RegionHints =
            try {
                hints()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                RegionHints()
            }
    }
}
