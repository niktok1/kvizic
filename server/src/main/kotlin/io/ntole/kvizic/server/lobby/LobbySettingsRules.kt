package io.ntole.kvizic.server.lobby

import io.ntole.kvizic.core.api.KvizicApi
import io.ntole.kvizic.core.lobby.LobbyKind
import io.ntole.kvizic.core.lobby.LobbySettingsDto
import io.ntole.kvizic.core.lobby.Visibility
import io.ntole.kvizic.core.question.Difficulty
import io.ntole.kvizic.server.player.DisplayNames

private val SEATS = KvizicApi.Limits.MIN_MAX_PLAYERS..KvizicApi.Limits.MAX_PLAYERS

/**
 * These settings with the room's name cleaned as a player's is, strangers seeing it all the same: cut to
 * [KvizicApi.Limits.MAX_ROOM_NAME_LENGTH], its control and disguising characters gone, its spaces collapsed,
 * and none at all when what is left is blank or holds a word from the short list of slurs and vulgarities.
 */
fun LobbySettingsDto.tidied(): LobbySettingsDto =
    copy(name = DisplayNames.clean(name, KvizicApi.Limits.MAX_ROOM_NAME_LENGTH))

/**
 * Why [settings] are not ones a host of a [kind] lobby with [members] could pick, or null when they
 * are: a count or time off the lists, a size off 2 to 8 or below who is there already, a topic no topic
 * has, or no visibility or difficulty. A solo run takes only its own fixed format, at any level.
 */
fun settingsProblem(
    settings: LobbySettingsDto,
    kind: LobbyKind,
    knownTopics: Set<String>,
    members: Int = 0,
): String? =
    when {
        kind == LobbyKind.SOLO && settings.copy(
            difficulty = LobbySettingsDto.SOLO.difficulty,
        ) != LobbySettingsDto.SOLO -> {
            "a solo run keeps its own format"
        }

        settings.questionCount !in KvizicApi.Limits.QUESTION_COUNTS -> {
            "questionCount is off the list"
        }

        settings.secondsPerQuestion !in KvizicApi.Limits.ANSWER_SECONDS -> {
            "secondsPerQuestion is off the list"
        }

        kind != LobbyKind.SOLO && settings.maxPlayers !in SEATS -> {
            "maxPlayers is off 2 to 8"
        }

        settings.maxPlayers < members -> {
            "maxPlayers is below the $members already here"
        }

        settings.visibility == Visibility.UNKNOWN -> {
            "no visibility"
        }

        settings.difficulty == Difficulty.UNKNOWN -> {
            "no difficulty"
        }

        settings.topics.size != settings.topics.toSet().size -> {
            "a topic twice"
        }

        settings.topics.any { it !in knownTopics } -> {
            "a topic no topic has"
        }

        else -> {
            null
        }
    }

/** The kind of lobby [settings] open, for one that is not a solo run. */
fun kindOf(settings: LobbySettingsDto): LobbyKind =
    if (settings.visibility == Visibility.PUBLIC) LobbyKind.PUBLIC else LobbyKind.PRIVATE
