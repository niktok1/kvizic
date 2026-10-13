package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.region.ContentLanguages

/**
 * A [LobbyConnection] whose reader said which content language they read, as a socket's hello does: the
 * lobby words what it sends a member in the language of their latest connection.
 */
interface ReaderConnection : LobbyConnection {
    /** One of `ContentLanguages.ALL`, as `ContentLanguages.of` maps the client's UI tag. */
    val language: String
}

/** The content language [this] connection's reader reads: Serbian Cyrillic for one that says none. */
internal val LobbyConnection.readerLanguage: String
    get() = (this as? ReaderConnection)?.language ?: ContentLanguages.SERBIAN
