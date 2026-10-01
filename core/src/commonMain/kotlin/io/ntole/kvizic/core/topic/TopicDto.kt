package io.ntole.kvizic.core.topic

import kotlinx.serialization.Serializable

/**
 * A topic questions are filed under: a stable [id] and a name in Serbian Cyrillic and in English. Topics
 * are server data, never an enum, so a topic added later is only an id an older client can't name.
 * [questionCount] is how many approved questions it has, so a picker can grey out thin topics. [groupId] is
 * the [TopicGroupDto] a picker lists it under, or none, after every group.
 */
@Serializable
public data class TopicDto(
    public val id: String,
    public val nameSr: String,
    public val nameEn: String,
    public val questionCount: Int = 0,
    public val groupId: String? = null,
)

/** What topics are grouped under in a picker, server data too, in the order a list is to show them. */
@Serializable
public data class TopicGroupDto(
    public val id: String,
    public val nameSr: String,
    public val nameEn: String,
)

/** Every topic, oldest first, and every group, in its order: a server before groups sends none. */
@Serializable
public data class TopicListDto(
    public val topics: List<TopicDto> = emptyList(),
    public val groups: List<TopicGroupDto> = emptyList(),
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
