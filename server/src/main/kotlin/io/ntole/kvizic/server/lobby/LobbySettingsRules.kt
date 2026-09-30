package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.Visibility

private val SEATS = KvizicApi.Limits.MIN_MAX_PLAYERS..KvizicApi.Limits.MAX_PLAYERS

/**
 * Why [settings] are not ones a host of a [kind] lobby with [members] could pick, or null when they
 * are: a count or time off the lists, a size off 2 to 8 or below who is there already, a topic no topic
 * has, or no visibility. A solo run takes only its own fixed format.
 */
fun settingsProblem(
    settings: LobbySettingsDto,
    kind: LobbyKind,
    knownTopics: Set<String>,
    members: Int = 0,
): String? =
    when {
        kind == LobbyKind.SOLO && settings != LobbySettingsDto.SOLO -> "a solo run keeps its own format"
        settings.questionCount !in KvizicApi.Limits.QUESTION_COUNTS -> "questionCount is off the list"
        settings.secondsPerQuestion !in KvizicApi.Limits.ANSWER_SECONDS -> "secondsPerQuestion is off the list"
        kind != LobbyKind.SOLO && settings.maxPlayers !in SEATS -> "maxPlayers is off 2 to 8"
        settings.maxPlayers < members -> "maxPlayers is below the $members already here"
        settings.visibility == Visibility.UNKNOWN -> "no visibility"
        settings.topics.size != settings.topics.toSet().size -> "a topic twice"
        settings.topics.any { it !in knownTopics } -> "a topic no topic has"
        else -> null
    }

/** The kind of lobby [settings] open, for one that is not a solo run. */
fun kindOf(settings: LobbySettingsDto): LobbyKind =
    if (settings.visibility == Visibility.PUBLIC) LobbyKind.PUBLIC else LobbyKind.PRIVATE
