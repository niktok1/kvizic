package io.ntole.kvizic.share

import io.ntole.kvizic.about.Site
import io.ntole.kvizic.room.CODE_LENGTH
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A link to a room, `https://kvizic.ntole.com/j/123456`, what a share of the room says: with the game, Android
 * opens the app at it ([opened]), which takes a seat in the room at once; without it, the site's page shows the
 * code and Google Play's page. Whatever build shares it, it is the one link, since a dev build's room is on dev
 * and the link only carries the code.
 */
object InviteLink {
    private const val PATH = "/j/"
    private val LINK =
        Regex("^https?://${Regex.escape(Site.BASE_URL.substringAfter("://"))}$PATH(\\d{$CODE_LENGTH})/?$")

    private val pending = MutableStateFlow<String?>(null)

    /** The code of a link opened, until the app takes it ([take]). */
    val code: StateFlow<String?> = pending.asStateFlow()

    fun of(code: String): String = Site.BASE_URL + PATH + code

    /** The room's code a [link] names, or none for any other link. */
    fun codeOf(link: String): String? = LINK.find(link.trim())?.groupValues?.get(1)

    /** The platform opened the app at [link]: a room's, the app takes a seat in it; any other, nothing. */
    fun opened(link: String) {
        codeOf(link)?.let { pending.value = it }
    }

    /** The app takes the code a link brought, [code], so it is acted on once. */
    fun take(code: String) {
        pending.compareAndSet(code, null)
    }
}
