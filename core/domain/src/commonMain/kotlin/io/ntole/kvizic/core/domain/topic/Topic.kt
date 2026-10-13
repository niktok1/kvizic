package io.ntole.kvizic.core.domain.topic

/**
 * A topic questions are filed under: server data, which a moderator adds to without a build, so the
 * client lists topics from the server ([TopicRepository]) rather than naming them itself.
 *
 * [id] is what a question, a lobby's settings and every request name the topic by, and it never changes.
 * [nameSr] is its name in Serbian, in Cyrillic, and [nameEn] in English; a moderator may put either
 * right, so nothing keeps a name longer than the list it came in. [questionCount] is how many approved
 * questions it has, so a picker can grey out a thin topic. [groupId] is the [TopicGroup] a picker lists it
 * under, or none, after every group. [names] is its name in the content languages, by tag
 * (`ContentLanguages`), `en` too; a server before them sends none, and [nameSr] is the Serbian one then.
 */
public data class Topic(
    public val id: String,
    public val nameSr: String,
    public val nameEn: String,
    public val questionCount: Int,
    public val groupId: String? = null,
    public val names: Map<String, String> = emptyMap(),
)

/**
 * What a picker lists topics under, so hundreds stay easy to find: server data too, named in Serbian and
 * English, and in the content languages by tag in [names], as a [Topic] is.
 */
public data class TopicGroup(
    public val id: String,
    public val nameSr: String,
    public val nameEn: String,
    public val names: Map<String, String> = emptyMap(),
)
