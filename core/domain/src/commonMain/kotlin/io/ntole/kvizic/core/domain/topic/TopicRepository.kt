package io.ntole.kvizic.core.domain.topic

import kotlinx.coroutines.flow.StateFlow

/**
 * Every topic questions can be filed under, as the server lists them. Implemented in `:core:data`.
 *
 * Kept in memory only, for the app's life, and read again when asked: a moderator adds topics and puts
 * their names right without a build. Reading needs no player session and touches none: the list is the
 * same for everybody.
 */
public interface TopicRepository {
    /** The topics as [refresh] last read them, in the server's order; empty until a read works. */
    public val topics: StateFlow<List<Topic>>

    /** The groups as [refresh] last read them, in the server's order; empty until a read works, or from a server before groups. */
    public val groups: StateFlow<List<TopicGroup>>

    /**
     * Reads every topic from the server again, keeps it in [topics], and returns it.
     *
     * @throws io.ntole.kvizic.core.domain.error.KvizicException on any failure, leaving [topics] as it was.
     */
    public suspend fun refresh(): List<Topic>
}
