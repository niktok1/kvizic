package io.ntole.kvizic.shop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ntole.kvizic.core.domain.analytics.Analytics
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.player.GetProfile
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.core.domain.player.SetAvatar
import io.ntole.kvizic.core.domain.player.ShopItem
import io.ntole.kvizic.core.domain.player.ShopItemKind
import io.ntole.kvizic.core.domain.player.Shopping
import io.ntole.kvizic.home.HomeFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the shop shows: the [items] it sells, the player's [profile] with their coins and what they own and wear,
 * the [tab] open, the item [selected] in it and tried on ([PLAIN] for the plain frame or crown everyone has),
 * whether a read or a buy is in flight ([busy]), and why the last failed.
 */
data class ShopState(
    val items: List<ShopItem> = emptyList(),
    val profile: Profile? = null,
    val tab: ShopItemKind = ShopItemKind.FRAME,
    val selected: String? = null,
    val busy: Boolean = false,
    val failure: HomeFailure? = null,
) {
    /** The item selected, among those sold. */
    val selectedItem: ShopItem? get() = items.firstOrNull { it.id == selected }

    /** What the shop's button does with the item selected, or none. */
    val action: ShopAction?
        get() {
            val profile = profile ?: return null
            if (selected == PLAIN) return ShopAction.Wear.takeIf { profile.worn(tab) != null }
            val item = selectedItem ?: return null
            return when {
                item.id !in profile.items -> {
                    ShopAction.Buy(
                        item.price,
                        missing = (item.price - profile.coins).coerceAtLeast(0),
                    )
                }

                profile.wears(item) -> {
                    if (item.kind == ShopItemKind.AVATAR) null else ShopAction.TakeOff
                }

                else -> {
                    ShopAction.Wear
                }
            }
        }

    companion object {
        /** The plain frame or crown, which everybody has: none worn. */
        const val PLAIN: String = ""
    }
}

/** What the shop's button does: buys for a price, of which the player lacks [Buy.missing], or puts on or takes off. */
sealed interface ShopAction {
    data class Buy(
        val price: Int,
        val missing: Int,
    ) : ShopAction

    data object Wear : ShopAction

    data object TakeOff : ShopAction
}

/** The id of what the player wears of [kind]: their avatar, frame, crown or title, none for the plain one. */
fun Profile.worn(kind: ShopItemKind): String? =
    when (kind) {
        ShopItemKind.AVATAR -> avatarId
        ShopItemKind.FRAME -> frameId
        ShopItemKind.CROWN -> crownId
        ShopItemKind.TITLE -> titleId
        ShopItemKind.OTHER -> null
    }

/** Whether the player wears [item]. */
fun Profile.wears(item: ShopItem): Boolean = worn(item.kind) == item.id

/**
 * The shop: what it sells and the player's profile, read each time it is shown ([shown]), a tab opened
 * ([selectTab]), an item in it tried on ([select]), then bought, put on or taken off with one button ([act]), one request at a time. A failure is
 * reported to [analytics] and shown until the next try.
 */
class ShopViewModel(
    private val shopping: Shopping,
    private val getProfile: GetProfile,
    private val setAvatar: SetAvatar,
    private val analytics: Analytics,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ShopState())

    val state: StateFlow<ShopState> = mutableState.asStateFlow()

    /** The shop is shown: what it sells and the player's coins are read again. */
    fun shown() =
        run(READ) {
            val items = shopping.items()
            val profile = getProfile()
            mutableState.update { it.copy(items = items, profile = profile) }
        }

    /** The tab of [kind] is opened, nothing in it tried on yet. */
    fun selectTab(kind: ShopItemKind) = mutableState.update { it.copy(tab = kind, selected = null, failure = null) }

    /** The item [id], or [ShopState.PLAIN], is tried on. */
    fun select(id: String) = mutableState.update { it.copy(selected = id, failure = null) }

    /** The button: buys the item selected, or puts it on, or takes it off. */
    fun act() {
        val state = mutableState.value
        val profile = state.profile ?: return
        val action = state.action ?: return
        val item = state.selectedItem
        run(BUY) {
            val after =
                when {
                    item == null -> {
                        profile.wearing(state.tab, null)
                    }

                    action is ShopAction.Buy -> {
                        shopping.buy(item.id).also {
                            analytics.track(
                                AnalyticsEvent.ITEM_BOUGHT,
                                mapOf(AnalyticsProperty.ITEM to item.id, AnalyticsProperty.PRICE to item.price),
                            )
                        }
                    }

                    action == ShopAction.Wear -> {
                        profile.wearing(item.kind, item.id).also {
                            analytics.track(AnalyticsEvent.ITEM_EQUIPPED, mapOf(AnalyticsProperty.ITEM to item.id))
                        }
                    }

                    else -> {
                        profile.wearing(item.kind, null)
                    }
                }
            mutableState.update { it.copy(profile = after) }
        }
    }

    /** The failure is taken down. */
    fun dismissFailure() = mutableState.update { it.copy(failure = null) }

    /** The profile with [id] worn as its [kind], none for the plain one; an avatar is never none. */
    private suspend fun Profile.wearing(
        kind: ShopItemKind,
        id: String?,
    ): Profile =
        when (kind) {
            ShopItemKind.AVATAR -> id?.let { setAvatar(it) } ?: this
            ShopItemKind.FRAME -> shopping.wear(id, titleId, crownId)
            ShopItemKind.CROWN -> shopping.wear(frameId, titleId, id)
            ShopItemKind.TITLE -> shopping.wear(frameId, id, crownId)
            ShopItemKind.OTHER -> this
        }

    private fun run(
        action: String,
        block: suspend () -> Unit,
    ) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, failure = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (failure: KvizicException) {
                analytics.track(
                    AnalyticsEvent.ERROR_SHOWN,
                    mapOf(AnalyticsProperty.CODE to failure.error.name, AnalyticsProperty.ACTION to action),
                )
                mutableState.update { it.copy(failure = HomeFailure(failure.error, failure.retryAfter)) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private companion object {
        /** What failed, as analytics name it. */
        const val READ = "shop"
        const val BUY = "shop_item"
    }
}
