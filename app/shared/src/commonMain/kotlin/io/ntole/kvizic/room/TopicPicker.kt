package io.ntole.kvizic.room

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.core.domain.language.SerbianScript
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.domain.topic.TopicGroup
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.KvizicIcon
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.TextInput
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.cued
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.navigation.BackTopBar

/**
 * The topics a room plays, picked from every topic the server lists, so hundreds stay easy to find: a search
 * that finds a topic by any part of its name, whatever the script, case or accents typed; Све теме, which
 * is none picked; and the groups, each opened or closed with a tap on its name, with a chip that picks
 * every topic in it, or none once all are. A topic with fewer questions than a game of [questionCount]
 * takes is greyed, since another topic tops it up. [picked] is what is picked, which [onChange] changes;
 * [onDone] goes back to the settings.
 */
@Composable
internal fun TopicPicker(
    picked: List<String>,
    topics: List<Topic>,
    groups: List<TopicGroup>,
    questionCount: Int,
    onChange: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    val words = LocalStrings.current.game
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    // Opened groups: all of them while there are few topics, none but the picked ones' once there are many.
    var opened by rememberSaveable {
        mutableStateOf(
            if (topics.size <= OPEN_ALL_UP_TO) {
                (groups.map { it.id } + OTHER).toSet()
            } else {
                topics.filter { it.id in picked }.map { it.groupId ?: OTHER }.toSet()
            },
        )
    }
    val sections = sectionsOf(topics, groups, words.otherTopics, language)
    val key = searchKey(query)
    val found = sections.mapNotNull { section -> section.matching(key, language) }
    Page {
        BackTopBar(onBack = onDone, title = words.topics)
        Spacer(Modifier.height(space.sm))
        TextInput(
            value = query,
            onValueChange = { query = it },
            label = words.searchTopics,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(space.sm))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xxs)) {
            if (key.isEmpty()) {
                item(key = "all") {
                    TopicLine(
                        name = words.allTopics,
                        picked = picked.isEmpty(),
                        onToggle = tapped("topics.all") { onChange(emptyList()) },
                    )
                }
            }
            if (found.isEmpty()) {
                item(key = "none") {
                    KvizicText(
                        words.noTopicFound,
                        Modifier.padding(vertical = space.md),
                        style = type.body,
                        color = colors.onPageMuted,
                    )
                }
            }
            found.forEach { section ->
                val open = key.isNotEmpty() || section.id in opened
                item(key = "group-${section.id}") {
                    GroupHeader(
                        section,
                        picked = section.topics.count { it.id in picked },
                        open = open,
                        onOpen =
                            tapped("topics.group", mapOf("group" to section.id)) {
                                opened = if (section.id in opened) opened - section.id else opened + section.id
                            },
                        onWhole =
                            tapped("topics.whole_group", mapOf("group" to section.id)) {
                                val ids = section.topics.map { it.id }
                                onChange(
                                    if (picked.containsAll(ids)) picked - ids.toSet() else (picked + ids).distinct(),
                                )
                            },
                    )
                }
                if (open) {
                    items(section.topics, key = { "topic-${it.id}" }) { topic ->
                        val isPicked = topic.id in picked
                        TopicLine(
                            name = topicName(topic, language),
                            picked = isPicked,
                            onToggle =
                                tapped("topics.topic", mapOf("topic" to topic.id)) {
                                    onChange(if (isPicked) picked - topic.id else picked + topic.id)
                                },
                            count = words.questionCount.of(topic.questionCount, language),
                            thin = topic.questionCount < questionCount,
                            indent = true,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(space.md))
        StageButton(
            if (picked.isEmpty()) words.done else "${words.done} · ${words.topicCount.of(picked.size, language)}",
            onClick = tapped("topics.done", onClick = onDone),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A group's line: its name, how many of its topics are picked, its chip for the whole group, and whether open. */
@Composable
private fun GroupHeader(
    section: Section,
    picked: Int,
    open: Boolean,
    onOpen: () -> Unit,
    onWhole: () -> Unit,
) {
    val words = LocalStrings.current.game
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = space.touchTarget)
            .padding(top = space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = space.touchTarget)
                .clickable(role = Role.Button, onClick = cued(Cue.TAP_SOFT, onOpen))
                .semantics { stateDescription = "$picked / ${section.topics.size}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            KvizicIcon(
                KvizicIcons.ChevronRight,
                contentDescription = null,
                tint = colors.onPageMuted,
                size = space.icon.small,
                modifier = Modifier.graphicsLayer { rotationZ = if (open) OPEN_TURN else 0f },
            )
            KvizicText(section.name, style = type.bodyStrong, maxLines = 1)
            KvizicText(
                "$picked / ${section.topics.size}",
                style = type.caption,
                color = if (picked > 0) colors.onPageAccent else colors.onPageMuted,
            )
        }
        Chip(words.wholeGroup, selected = picked == section.topics.size && picked > 0, onClick = onWhole)
    }
}

/** A line to pick: its name, how many questions it holds, greyed when [thin], and a tick while [picked]. */
@Composable
private fun TopicLine(
    name: String,
    picked: Boolean,
    onToggle: () -> Unit,
    count: String? = null,
    thin: Boolean = false,
    indent: Boolean = false,
) {
    val words = LocalStrings.current.game
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val toggle = cued(if (picked) Cue.TOGGLE_OFF else Cue.TOGGLE_ON) { onToggle() }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = space.touchTarget)
            .toggleable(value = picked, role = Role.Checkbox, onValueChange = { toggle() })
            .then(if (indent) Modifier.padding(start = space.lg) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Column(Modifier.weight(1f)) {
            KvizicText(name, style = type.body, color = if (thin) colors.onPageMuted else colors.onPage, maxLines = 1)
            if (count != null) {
                KvizicText(
                    if (thin) "$count · ${words.fewQuestions}" else count,
                    style = type.caption,
                    color = colors.onPageMuted,
                    maxLines = 1,
                )
            }
        }
        if (picked) {
            KvizicIcon(
                KvizicIcons.Check,
                contentDescription = null,
                tint = colors.onPageAccent,
                size = space.icon.small,
            )
        } else {
            Spacer(Modifier.width(space.icon.small))
        }
    }
}

/** A group as the picker lists it, or the topics in none under [OTHER]. */
private data class Section(
    val id: String,
    val name: String,
    val topics: List<Topic>,
) {
    /** The section with only the topics [key] finds, or none when it finds none; all of it for no key. */
    fun matching(
        key: String,
        language: Language,
    ): Section? {
        if (key.isEmpty()) return this
        val kept = topics.filter { key in searchKey(topicName(it, language)) || key in searchKey(it.nameEn) }
        return if (kept.isEmpty()) null else copy(topics = kept)
    }
}

/** Every group in its order with its topics in theirs, and the topics in no group last, under [otherName]. */
private fun sectionsOf(
    topics: List<Topic>,
    groups: List<TopicGroup>,
    otherName: String,
    language: Language,
): List<Section> {
    val known = groups.map { it.id }.toSet()
    val grouped =
        groups.map { group ->
            Section(group.id, groupName(group, language), topics.filter { it.groupId == group.id })
        }
    val other = Section(OTHER, otherName, topics.filter { it.groupId == null || it.groupId !in known })
    return (grouped + other).filter { it.topics.isNotEmpty() }
}

internal fun groupName(
    group: TopicGroup,
    language: Language,
): String =
    when (language) {
        Language.SERBIAN_CYRILLIC -> group.nameSr
        Language.SERBIAN_LATIN -> SerbianScript.toLatin(group.nameSr)
        Language.ENGLISH -> group.nameEn
    }

/**
 * [text] as a search compares it: Serbian Latin, lower case, and without its accents, so a query typed in
 * either script, any case, on a keyboard with no Serbian letters, finds a name: *spo*, *Спо* and *SPO* find
 * *Спорт*, *nauka* finds *Наука*, and *djak* and *đak* alike find *Ђак*.
 */
internal fun searchKey(text: String): String =
    SerbianScript
        .toLatin(text.trim())
        .lowercase()
        .replace("đ", "dj")
        .map { char ->
            when (char) {
                'č', 'ć' -> 'c'
                'š' -> 's'
                'ž' -> 'z'
                else -> char
            }
        }.joinToString("")

/**
 * The topics picked, as a room's chip names them: Све теме for none, the names of one or two, and two
 * names and how many more past that.
 */
internal fun topicsSummary(
    picked: List<String>,
    topics: List<Topic>,
    language: Language,
    allTopics: String,
): String {
    if (picked.isEmpty()) return allTopics
    val names = picked.map { topicNameOf(it, topics, language) }
    if (names.size <= SUMMARY_NAMES) return names.joinToString(", ")
    // A count is the same in every language, so it is no string of theirs.
    return names.take(SUMMARY_NAMES).joinToString(", ") + " +" + (names.size - SUMMARY_NAMES)
}

/** The id the topics in no group are listed under. */
private const val OTHER = "_other"

/** Up to how many topics every group starts open. */
private const val OPEN_ALL_UP_TO = 20

/** How far an open group's chevron turns, to point down. */
private const val OPEN_TURN = 90f

/** How many topics' names a summary gives before it counts the rest. */
private const val SUMMARY_NAMES = 2
