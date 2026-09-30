package io.ntole.kvizic.server.topic

import io.ntole.kvizic.server.db.Db

/**
 * The topic ids, in memory, for what must not read the database: a lobby checking its settings. Read at
 * boot and again after every topic the moderator adds; nothing deletes a topic, so a stale copy only
 * lacks the newest.
 */
class TopicCatalog(
    private val db: Db,
) {
    @Volatile
    var ids: Set<String> = emptySet()
        private set

    suspend fun refresh() {
        ids = db.query { TopicStore.ids() }
    }
}
