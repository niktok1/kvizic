package io.ntole.kvizic.core.domain.region

/**
 * What the server is told of the player: the country they play from, on every REST call, and the language the
 * game is shown in, in every socket's hello. Kept by the app, which detects both at a first launch and changes
 * them as the player picks; read by the data layer each time it tells the server, so a change applies to the
 * next request and the next socket.
 *
 * The server takes the country at every seat taken and the language at every connection, so a player already
 * in a room plays on with those they came in with until they sit down or connect again; a reconnection is a
 * connection. The Settings screen where both are picked is reached from Home, never from a room.
 */
public interface PlayerRegion {
    /** Where the player plays from, now. */
    public fun currentCountry(): Country

    /** The tag of the language the game is shown in now, as the device keeps it: `sr-Cyrl`, `sr-Latn`, `hr`… */
    public fun currentLanguageTag(): String
}
