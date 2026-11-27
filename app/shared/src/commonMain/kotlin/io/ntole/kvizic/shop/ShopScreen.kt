package io.ntole.kvizic.shop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.analytics.tappedAt
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.core.domain.player.ShopItem
import io.ntole.kvizic.core.domain.player.ShopItemKind
import io.ntole.kvizic.core.domain.region.ContentLanguages
import io.ntole.kvizic.design.avatar.AvatarArt
import io.ntole.kvizic.design.avatar.FrameArt
import io.ntole.kvizic.design.component.Avatar
import io.ntole.kvizic.design.component.AvatarSize
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.KvizicIcon
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.component.PanelKind
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.cued
import io.ntole.kvizic.home.Notice
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.failureText
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.loading.LoadingSpinner
import io.ntole.kvizic.room.nameIn
import io.ntole.kvizic.room.shown

/** What the shop's buttons do. */
@Immutable
class ShopActions(
    val select: (String) -> Unit = {},
    val act: () -> Unit = {},
    val retry: () -> Unit = {},
    val dismissFailure: () -> Unit = {},
)

/**
 * The shop: the player as they would look with the item selected on, their coins and how coins are earned, then
 * the frames, titles and animals it sells that this build can show, each with its price, or marked as the player's
 * or worn, and at the foot the one button that buys, puts on or takes off the item selected.
 */
@Composable
fun ShopScreen(
    state: ShopState,
    actions: ShopActions,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val words = strings.economy
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val profile = state.profile
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val failure = state.failure
        if (profile == null) {
            if (failure != null) {
                StageButton(
                    strings.tryAgain,
                    onClick = tapped("shop.try_again", onClick = actions.retry),
                    modifier = Modifier.padding(space.xl),
                    kind = ButtonKind.QUIET,
                )
            } else {
                LoadingSpinner(name = strings.loading, modifier = Modifier.padding(space.xl))
            }
            return@Box
        }
        val sold = state.items.filter { it.isShown() }
        val selected = state.selectedItem
        val pick = tappedAt("shop.item") { index -> actions.select(sold[index].id) }
        Column(
            Modifier
                .widthIn(max = space.contentWidth)
                .fillMaxSize()
                .padding(horizontal = space.screen, vertical = space.md),
            verticalArrangement = Arrangement.spacedBy(space.md),
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(space.md),
            ) {
                val tried = { kind: ShopItemKind -> selected?.takeIf { it.kind == kind }?.id }
                Avatar(
                    tried(ShopItemKind.AVATAR) ?: profile.avatarId,
                    seat = 0,
                    size = AvatarSize.XL,
                    frame = tried(ShopItemKind.FRAME) ?: profile.frameId,
                )
                KvizicText(shown(profile.displayName), style = type.headline, textAlign = TextAlign.Center)
                (selected?.takeIf { it.kind == ShopItemKind.TITLE } ?: sold.firstOrNull { it.id == profile.titleId })
                    ?.let { KvizicText(titleOf(it), style = type.bodyStrong, color = colors.onPageAccent) }
                Balance(profile.coins)
                KvizicText(
                    words.howToEarn,
                    style = type.caption,
                    color = colors.onPageMuted,
                    textAlign = TextAlign.Center,
                )
                if (failure != null) {
                    Notice(
                        strings.failureText(failure.error, failure.retryAfter),
                        onDismiss = tapped("shop.failure_ok", onClick = actions.dismissFailure),
                    )
                }
                listOf(
                    ShopItemKind.FRAME to words.frames,
                    ShopItemKind.TITLE to words.titles,
                    ShopItemKind.AVATAR to words.animals,
                ).forEach { (kind, heading) ->
                    val ofKind = sold.withIndex().filter { it.value.kind == kind }
                    if (ofKind.isEmpty()) return@forEach
                    KvizicText(heading, Modifier.fillMaxWidth(), style = type.label, color = colors.onPageMuted)
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(space.sm, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(space.sm),
                    ) {
                        ofKind.forEach { (index, item) ->
                            ItemTile(item, profile, chosen = item.id == state.selected, onClick = { pick(index) })
                        }
                    }
                }
            }
            ActionButton(state, onClick = actions.act)
        }
    }
}

/** A balance: a coin and how many, in the accent, said in words to a screen reader; [onClick] makes it a button. */
@Composable
fun Balance(
    coins: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val words = LocalStrings.current.economy
    val said = words.coins.of(coins, LocalLanguage.current)
    val tap = onClick?.let { cued(Cue.TAP_SOFT, it) }
    Box(
        modifier.clearAndSetSemantics {
            contentDescription = if (tap != null) "${words.shop}, $said" else said
            if (tap != null) {
                role = Role.Button
                onClick {
                    tap()
                    true
                }
            }
        },
    ) {
        Chip(coins.toString(), icon = KvizicIcons.Coin, tone = ChipTone.ACCENT, onClick = onClick)
    }
}

/** One item for sale: drawn on the player, or its words, over its price or what the player has of it. */
@Composable
private fun ItemTile(
    item: ShopItem,
    profile: Profile,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    val words = LocalStrings.current.economy
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val owned = item.id in profile.items
    val worn = profile.wears(item)
    val standing =
        when {
            worn -> words.worn
            owned -> words.owned
            else -> words.coins.of(item.price, language)
        }
    val said = "${nameOf(item, language)}, $standing"
    Panel(
        Modifier
            .semantics(mergeDescendants = true) {
                contentDescription = said
                selected = chosen
            }.clickable(role = Role.Button, onClick = cued(Cue.TAP_SOFT, onClick)),
        kind = if (chosen) PanelKind.OWN else PanelKind.PLAIN,
        padding = space.xs,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (item.kind) {
                ShopItemKind.FRAME -> {
                    Avatar(profile.avatarId, seat = 0, size = AvatarSize.LG, frame = item.id)
                }

                ShopItemKind.AVATAR -> {
                    Avatar(
                        item.id,
                        seat = AvatarArt.SHOP.indexOf(item.id) + 1,
                        size = AvatarSize.LG,
                    )
                }

                else -> {
                    KvizicText(titleOf(item), style = type.bodyStrong, maxLines = 1)
                }
            }
            Spacer(Modifier.height(space.xxs))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.xxs),
            ) {
                when {
                    worn -> {
                        KvizicIcon(
                            KvizicIcons.Check,
                            contentDescription = null,
                            tint = colors.primary,
                            size = space.icon.small,
                        )
                    }

                    owned -> {
                        KvizicText(words.owned, style = type.caption, color = colors.onRaisedMuted)
                    }

                    else -> {
                        KvizicIcon(
                            KvizicIcons.Coin,
                            contentDescription = null,
                            tint = colors.primary,
                            size = space.icon.small,
                        )
                        KvizicText(item.price.toString(), style = type.caption)
                    }
                }
            }
        }
    }
}

/** The shop's one button for the item selected: its price, how many coins are lacking above it, or on or off. */
@Composable
private fun ActionButton(
    state: ShopState,
    onClick: () -> Unit,
) {
    val words = LocalStrings.current.economy
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val action = state.action ?: return
    val buy = action as? ShopAction.Buy
    val (text, element) =
        when (action) {
            is ShopAction.Buy -> action.price.toString() to "shop.buy"
            ShopAction.Wear -> words.wear to "shop.wear"
            ShopAction.TakeOff -> words.takeOff to "shop.take_off"
        }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (buy != null && buy.missing > 0) {
            KvizicText(
                words.missing.fill(words.coins.of(buy.missing, language)),
                style = type.caption,
                color = colors.onPageMuted,
            )
            Spacer(Modifier.height(space.xs))
        }
        StageButton(
            text,
            onClick = tapped(element, onClick = onClick),
            modifier = Modifier.fillMaxWidth(),
            kind = if (action == ShopAction.TakeOff) ButtonKind.SECONDARY else ButtonKind.PRIMARY,
            icon = KvizicIcons.Coin.takeIf { buy != null },
            enabled = !state.busy && (buy == null || buy.missing == 0),
        )
    }
}

/** Whether this build can show [this]: a frame or an avatar it draws, or a title. */
private fun ShopItem.isShown(): Boolean =
    when (kind) {
        ShopItemKind.FRAME -> FrameArt.isDrawn(id)
        ShopItemKind.AVATAR -> AvatarArt.isDrawn(id)
        ShopItemKind.TITLE -> names.isNotEmpty()
        ShopItemKind.OTHER -> false
    }

/** A title's words in the language shown. */
@Composable
private fun titleOf(item: ShopItem): String = titleIn(item, LocalLanguage.current)

private fun titleIn(
    item: ShopItem,
    language: Language,
): String =
    nameIn(
        language,
        item.names[ContentLanguages.SERBIAN].orEmpty(),
        item.names[Language.ENGLISH.tag].orEmpty(),
        item.names,
    )

/** An item's name to a screen reader. */
@Composable
private fun nameOf(
    item: ShopItem,
    language: Language,
): String {
    val words = LocalStrings.current.economy
    return when (item.kind) {
        ShopItemKind.FRAME -> words.frameNames.getOrElse(FrameArt.DRAWN.indexOf(item.id)) { item.id }
        ShopItemKind.AVATAR -> words.animalNames.getOrElse(AvatarArt.SHOP.indexOf(item.id)) { item.id }
        else -> titleIn(item, language)
    }
}
