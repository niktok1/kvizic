package io.ntole.kvizic.core.topic

import kotlinx.serialization.Serializable

/**
 * A topic questions are filed under: a stable [id] and a name in Serbian Cyrillic and in English. Topics
 * are server data, never an enum, so a topic added later is only an id an older client can't name.
 * [questionCount] is how many approved questions a solo player in the caller's country could be asked, so a
 * picker can grey out thin topics: the common ones and those scoped to that country, the country being the
 * request's `KvizicApi.Headers.COUNTRY` (none is Serbia; one not known, `Country.UNKNOWN`, the common ones
 * alone). That is in `KvizicApi.Paths.TOPICS`; a moderator's route counts the whole approved bank, and a topic
 * just added 0. [groupId] is the [TopicGroupDto] a picker lists it under, or none, after every group. [names]
 * holds its name in the content languages by tag (`ContentLanguages`), `en` too; a server before them sends
 * none.
 */
@Serializable
public data class TopicDto(
    public val id: String,
    public val nameSr: String,
    public val nameEn: String,
    public val questionCount: Int = 0,
    public val groupId: String? = null,
    public val names: Map<String, String> = emptyMap(),
)

/**
 * What topics are grouped under in a picker, server data too, in the order a list is to show them, with
 * [names] by tag as a [TopicDto]'s.
 */
@Serializable
public data class TopicGroupDto(
    public val id: String,
    public val nameSr: String,
    public val nameEn: String,
    public val names: Map<String, String> = emptyMap(),
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
