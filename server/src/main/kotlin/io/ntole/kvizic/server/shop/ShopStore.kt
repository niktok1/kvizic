package io.ntole.kvizic.server.shop

import io.ntole.kvizic.core.player.ShopItemDto
import io.ntole.kvizic.core.player.ShopItemKind
import io.ntole.kvizic.server.db.PlayerItems
import io.ntole.kvizic.server.db.Players
import io.ntole.kvizic.server.db.Profiles
import io.ntole.kvizic.server.plugins.ApiFailure
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.minus
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update

/** A player's coins and what they bought with them. Every function must run inside a transaction. */
object ShopStore {
    /**
     * Buys [item] for [playerId] at [now]: nothing for one they own already. The player's row is locked first,
     * as `ResultWriter` locks it before it pays them; the coins come off in one compare-and-set, and the key of
     * what they own refuses a second row, so two buys at once never charge twice.
     */
    fun buy(
        playerId: String,
        item: ShopItemDto,
        now: Long,
    ) {
        Players
            .select(Players.id)
            .where { Players.id eq playerId }
            .forUpdate()
            .singleOrNull()
            ?: throw ApiFailure.unauthorized("unknown player")
        if (owns(playerId, item.id)) return
        val charged =
            Profiles.update({ (Profiles.playerId eq playerId) and (Profiles.coins greaterEq item.price) }) { row ->
                row[coins] = coins - item.price
            }
        if (charged == 0) throw ApiFailure.notEnoughCoins()
        PlayerItems.insert { row ->
            row[PlayerItems.playerId] = playerId
            row[itemId] = item.id
            row[pricePaid] = item.price
            row[boughtAt] = now
        }
    }

    /** Whether [playerId] owns [itemId]. */
    fun owns(
        playerId: String,
        itemId: String,
    ): Boolean =
        PlayerItems
            .select(PlayerItems.itemId)
            .where { (PlayerItems.playerId eq playerId) and (PlayerItems.itemId eq itemId) }
            .any()

    /** What [playerId] owns, in the order they bought it. */
    fun owned(playerId: String): List<String> =
        PlayerItems
            .select(PlayerItems.itemId)
            .where { PlayerItems.playerId eq playerId }
            .orderBy(PlayerItems.boughtAt)
            .map { it[PlayerItems.itemId] }

    /**
     * Puts [frameId] and [titleId] on [playerId], each a frame or a title of theirs, or none: false when there
     * is no such player. One they do not own, or of another kind, is a request no client sends.
     */
    fun wear(
        playerId: String,
        frameId: String?,
        titleId: String?,
    ): Boolean {
        listOf(frameId to ShopItemKind.FRAME, titleId to ShopItemKind.TITLE).forEach { (id, kind) ->
            if (id != null && (Shop.item(id)?.kind != kind || !owns(playerId, id))) {
                throw ApiFailure.validation("not a $kind of theirs: $id")
            }
        }
        return Profiles.update({ Profiles.playerId eq playerId }) { row ->
            row[Profiles.frameId] = frameId
            row[Profiles.titleId] = titleId
        } > 0
    }
}
