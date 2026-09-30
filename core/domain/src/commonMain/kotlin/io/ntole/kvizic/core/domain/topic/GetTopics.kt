package io.ntole.kvizic.core.domain.topic

/**
 * Reads every topic from the server again, as a picker does each time it opens, and keeps it in
 * [TopicRepository.topics] for every screen that names one.
 *
 * Ensures no session, unlike the player's use cases: the list is the same for everybody, so the server
 * reads none, and a client with no player, the moderation app, reads it too.
 */
public class GetTopics(
    private val topics: TopicRepository,
) {
    /** @throws io.ntole.kvizic.core.domain.error.KvizicException as [TopicRepository.refresh] does. */
    public suspend operator fun invoke(): List<Topic> = topics.refresh()
}
