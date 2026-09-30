package io.ntole.kvizic.server.player

import io.ntole.kvizic.core.player.Avatars
import io.ntole.kvizic.core.player.NameSource
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.Profiles
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.random.Random

object PlayerStore {
    /** A player as others see them in a lobby. */
    data class Player(
        val id: String,
        val displayName: String,
        val nameSource: NameSource,
        val avatarId: String,
    )

    /**
     * Mints a guest with a random avatar and a nickname to match it, and their profile, with no session
     * yet: the mint opens one in the same transaction. Must run inside a transaction.
     */
    fun createGuest(
        random: Random = Random.Default,
        now: Long = System.currentTimeMillis(),
    ): Player {
        val id = UUID.randomUUID().toString()
        val avatar = Avatars.ALL.random(random)
        val name = Nicknames.forAvatar(avatar, random)

        Players.insert { row ->
            row[Players.id] = id
            row[createdAt] = now
            row[displayName] = name
            row[nameSource] = NameSource.GENERATED
        }
        Profiles.insert { row ->
            row[playerId] = id
            row[avatarId] = avatar
        }

        return Player(id = id, displayName = name, nameSource = NameSource.GENERATED, avatarId = avatar)
    }

    /** The player [id], or null when there is none. Must run inside a transaction. */
    fun find(id: String): Player? =
        Players
            .join(Profiles, JoinType.INNER, Players.id, Profiles.playerId)
            .select(Players.id, Players.displayName, Players.nameSource, Profiles.avatarId)
            .where { Players.id eq id }
            .limit(1)
            .firstOrNull()
            ?.let { row ->
                Player(
                    id = row[Players.id],
                    displayName = row[Players.displayName],
                    nameSource = row[Players.nameSource],
                    avatarId = row[Profiles.avatarId],
                )
            }

    /** Names [id] by their Play Games name, already cleaned. Must run inside a transaction. */
    fun setPlayGamesName(
        id: String,
        name: String,
    ) {
        Players.update({ Players.id eq id }) { row ->
            row[displayName] = name
            row[nameSource] = NameSource.PLAY_GAMES
        }
    }

    /** Gives [id] the avatar [avatarId], one of `Avatars.ALL`, and returns whether there was such a player. */
    fun setAvatar(
        id: String,
        avatarId: String,
    ): Boolean = Profiles.update({ Profiles.playerId eq id }) { row -> row[Profiles.avatarId] = avatarId } > 0
}
