package io.ntole.kvizic.core.topic

import kotlinx.serialization.Serializable

/**
 * A topic questions are filed under: a stable [id] and a name in Serbian Cyrillic and in English. Topics
 * are server data, never an enum, so a topic added later is only an id an older client can't name.
 * [questionCount] is how many approved questions it has, so a picker can grey out thin topics.
 */
@Serializable
public data class TopicDto(
    public val id: String,
    public val nameSr: String,
    public val nameEn: String,
    public val questionCount: Int = 0,
)

@Serializable
public data class TopicListDto(
    public val topics: List<TopicDto> = emptyList(),
)

/** A moderator adding a topic. The id is given; it never changes once made. */
@Serializable
public data class CreateTopicRequest(
    public val id: String,
    public val nameSr: String,
    public val nameEn: String,
)

/** A moderator setting both of a topic's names. */
@Serializable
public data class RenameTopicRequest(
    public val id: String,
    public val nameSr: String,
    public val nameEn: String,
)
