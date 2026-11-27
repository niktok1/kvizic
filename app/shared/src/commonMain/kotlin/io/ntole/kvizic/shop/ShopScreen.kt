package io.ntole.kvizic.shop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
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
import io.ntole.kvizic.design.avatar.AvatarArt
import io.ntole.kvizic.design.avatar.CrownArt
import io.ntole.kvizic.design.avatar.FrameArt
import io.ntole.kvizic.design.component.Avatar
import io.ntole.kvizic.design.component.AvatarSize
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.Ducat
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
import io.ntole.kvizic.language.EconomyStrings
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.failureText
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.loading.LoadingSpinner

/** What the shop's buttons do. */
@Immutable
class ShopActions(
    val selectTab: (ShopItemKind) -> Unit = {},
    val select: (String) -> Unit = {},
    val act: () -> Unit = {},
    val retry: () -> Unit = {},
    val dismissFailure: () -> Unit = {},
)

/** The shop's tabs, in their order. */
private val TABS = listOf(ShopItemKind.FRAME, ShopItemKind.CROWN, ShopItemKind.AVATAR)

/**
 * The shop: a stage where the player stands on a pedestal wearing what they try on, a crown over their head while
 * the crowns are open; the tabs as raised keys; the open tab's items in a row, from the plain one everybody has
 * to the richest, each with its price or marked as the player's; and the one button that buys, puts on or takes
 * off the item tried on.
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
        val plain = listOf(ShopState.PLAIN).filter { state.tab != ShopItemKind.AVATAR }
        val shown = plain + state.items.filter { it.kind == state.tab && it.isDrawn() }.map { it.id }
        val pick = tappedAt("shop.item") { index -> actions.select(shown[index]) }
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
                ShopStage(state, profile)
                KvizicText(
                    words.howToEarn,
                    style = type.caption,
                    color = colors.onPageMuted,
                    textAlign = TextAlign.Center,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(space.xs, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    TABS.forEach { kind ->
                        StageButton(
                            words.tabOf(kind),
                            onClick = tapped("shop.tab") { actions.selectTab(kind) },
                            modifier = Modifier.semantics { selected = kind == state.tab },
                            kind = if (kind == state.tab) ButtonKind.PRIMARY else ButtonKind.DARK,
                            size = ButtonSize.SMALL,
                        )
                    }
                }
                if (failure != null) {
                    Notice(
                        strings.failureText(failure.error, failure.retryAfter),
                        onDismiss = tapped("shop.failure_ok", onClick = actions.dismissFailure),
                    )
                }
                val next = state.items.firstOrNull { it.kind == state.tab && it.id !in profile.items }?.id
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                ) {
                    shown.forEachIndexed { index, id ->
                        ItemCard(id, state, profile, next = id == next, onClick = { pick(index) })
                    }
                }
            }
            ActionButton(state, onClick = actions.act)
        }
    }
}

/** The player on a pedestal, wearing what they try on, with its name under them. */
@Composable
private fun ShopStage(
    state: ShopState,
    profile: Profile,
) {
    val words = LocalStrings.current.economy
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val tried = { kind: ShopItemKind, worn: String? ->
        if (state.tab == kind && state.selected != null) state.selected.ifEmpty { null } else worn
    }
    val animal = state.selectedItem?.takeIf { it.kind == ShopItemKind.AVATAR }?.id ?: profile.avatarId
    val whole = space.avatar.xl
    Panel(Modifier.fillMaxWidth(), kind = PanelKind.SCREEN, padding = space.md) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(space.xl))
            Avatar(
                animal,
                seat = 0,
                size = AvatarSize.XL,
                host = state.tab == ShopItemKind.CROWN,
                frame = tried(ShopItemKind.FRAME, profile.frameId),
                crown = tried(ShopItemKind.CROWN, profile.crownId),
            )
            Canvas(Modifier.size(whole * PEDESTAL_WIDTH, whole * PEDESTAL_HEIGHT)) { drawOval(colors.raisedSide) }
            Spacer(Modifier.height(space.xs))
            KvizicText(
                state.selected?.let { nameOf(it, state.tab) } ?: words.tabOf(state.tab),
                style = type.caption,
                color = colors.onRaisedMuted,
            )
        }
    }
}

/** One item of the open tab: drawn on the player, its name, and its price or what the player has of it. */
@Composable
private fun ItemCard(
    id: String,
    state: ShopState,
    profile: Profile,
    next: Boolean,
    onClick: () -> Unit,
) {
    val words = LocalStrings.current.economy
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val item = state.items.firstOrNull { it.id == id }
    val worn = if (item == null) profile.worn(state.tab) == null else profile.wears(item)
    val price = item?.takeUnless { it.id in profile.items }?.price
    val name = nameOf(id, state.tab)
    val standing =
        when {
            worn -> words.worn
            price == null -> words.owned
            else -> words.coins.of(price, language)
        }
    Panel(
        Modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "$name, $standing"
                selected = id == state.selected
            }.clickable(role = Role.Button, onClick = cued(Cue.TAP_SOFT, onClick)),
        kind = if (id == state.selected) PanelKind.OWN else PanelKind.PLAIN,
        padding = space.sm,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space.xxs),
        ) {
            when (state.tab) {
                ShopItemKind.AVATAR -> {
                    Avatar(id, seat = AvatarArt.SHOP.indexOf(id) + 1, size = AvatarSize.MD)
                }

                ShopItemKind.CROWN -> {
                    Spacer(Modifier.height(space.sm))
                    Avatar(profile.avatarId, seat = 0, size = AvatarSize.MD, host = true, crown = id.ifEmpty { null })
                }

                else -> {
                    Avatar(profile.avatarId, seat = 0, size = AvatarSize.MD, frame = id.ifEmpty { null })
                }
            }
            KvizicText(name, style = type.caption, maxLines = 1)
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

                    price == null -> {
                        KvizicText(words.owned, style = type.caption, color = colors.onRaisedMuted)
                    }

                    else -> {
                        Ducat(space.icon.small)
                        KvizicText(
                            price.toString(),
                            style = type.caption,
                            color = if (next) colors.primary else colors.onRaised,
                        )
                    }
                }
            }
        }
    }
}

/** The shop's one button for the item tried on: its price, how many coins are lacking above it, or on or off. */
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
            enabled = !state.busy && (buy == null || buy.missing == 0),
            trailing = buy?.let { { Ducat(space.icon.medium) } },
        )
    }
}

/** A balance: a ducat and how many, said in words to a screen reader; [onClick] makes it a button. */
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
        CoinChip(coins.toString(), onClick = onClick)
    }
}

/** A chip with a ducat before [text]: a balance, or the coins a game paid. */
@Composable
fun CoinChip(
    text: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Chip(text, modifier, leading = { Ducat(KvizicTheme.space.icon.small) }, onClick = onClick)
}

/** Whether this build draws [this]: a frame, a crown or an animal it has a drawing for. */
private fun ShopItem.isDrawn(): Boolean =
    when (kind) {
        ShopItemKind.FRAME -> FrameArt.isDrawn(id)
        ShopItemKind.CROWN -> CrownArt.isDrawn(id)
        ShopItemKind.AVATAR -> AvatarArt.isDrawn(id)
        ShopItemKind.TITLE, ShopItemKind.OTHER -> false
    }

/** The name of the item [id] of [kind], the plain one's for [ShopState.PLAIN]. */
@Composable
private fun nameOf(
    id: String,
    kind: ShopItemKind,
): String {
    val words = LocalStrings.current.economy
    val tier = {
        drawn: List<String>,
        plain: String,
        ->
        if (id.isEmpty()) plain else words.tiers.getOrNull(drawn.indexOf(id))
    }
    return when (kind) {
        ShopItemKind.AVATAR -> words.animalNames.getOrNull(AvatarArt.SHOP.indexOf(id))
        ShopItemKind.CROWN -> tier(CrownArt.DRAWN, words.plainCrown)
        else -> tier(FrameArt.DRAWN, words.plainFrame)
    } ?: id
}

/** A tab's name. */
private fun EconomyStrings.tabOf(kind: ShopItemKind): String =
    when (kind) {
        ShopItemKind.CROWN -> crowns
        ShopItemKind.AVATAR -> animals
        else -> frames
    }

/** The pedestal's size, in shares of the avatar's. */
private const val PEDESTAL_WIDTH = 1.4f
private const val PEDESTAL_HEIGHT = 0.18f
