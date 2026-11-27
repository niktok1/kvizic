package io.ntole.kvizic.shop

import io.ntole.kvizic.analytics.RecordingAnalytics
import io.ntole.kvizic.analytics.RecordingAnalytics.Recorded
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.GameError
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.player.GetProfile
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerRepository
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.core.domain.player.SetAvatar
import io.ntole.kvizic.core.domain.player.ShopItem
import io.ntole.kvizic.core.domain.player.ShopItemKind
import io.ntole.kvizic.core.domain.player.Shopping
import io.ntole.kvizic.core.domain.session.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ShopViewModelTest {
    private val main = StandardTestDispatcher()
    private val players = FakeShop()
    private val analytics = RecordingAnalytics()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the button buys what the player lacks and puts on or takes off what they own`() {
        val profile =
            PROFILE.copy(
                items = listOf("frame.double", "title.lucky", "eagle"),
                frameId = "frame.double",
                avatarId = "eagle",
            )
        val cases =
            mapOf(
                "frame.dashes" to ShopAction.Buy(100, missing = 0),
                "frame.bulbs" to ShopAction.Buy(200, missing = 80),
                "frame.double" to ShopAction.TakeOff,
                "title.lucky" to ShopAction.Wear,
                "eagle" to null,
            )
        cases.forEach { (id, expected) -> assertEquals(expected, ShopState(ITEMS, profile, selected = id).action, id) }
    }

    @Test
    fun `an item is bought then put on then taken off and a refusal is shown`() =
        runTest(main) {
            val shop =
                ShopViewModel(
                    Shopping(players, players),
                    GetProfile(players, players),
                    SetAvatar(players, players),
                    analytics,
                )
            shop.shown()
            testScheduler.advanceUntilIdle()
            shop.select("frame.dashes")

            repeat(3) {
                shop.act()
                testScheduler.advanceUntilIdle()
            }
            assertEquals(
                listOf("frame.dashes") to null,
                shop.state.value.profile
                    ?.let { it.items to it.frameId },
            )
            assertEquals(
                20,
                shop.state.value.profile
                    ?.coins,
            )
            assertEquals(
                listOf(
                    Recorded(
                        AnalyticsEvent.ITEM_BOUGHT,
                        mapOf(
                            AnalyticsProperty.ITEM to "frame.dashes",
                            AnalyticsProperty.PRICE to 100,
                        ),
                    ),
                    Recorded(AnalyticsEvent.ITEM_EQUIPPED, mapOf(AnalyticsProperty.ITEM to "frame.dashes")),
                ),
                analytics.recorded,
            )

            players.refuse = KvizicException(GameError.NOT_ENOUGH_COINS)
            shop.select("frame.double")
            shop.act()
            testScheduler.advanceUntilIdle()
            assertEquals(
                GameError.NOT_ENOUGH_COINS,
                shop.state.value.failure
                    ?.error,
            )
        }

    @Test
    fun `a shop that cannot be read offers Try again`() =
        runTest(main) {
            players.readFails = KvizicException(GameError.ITEM_NOT_FOUND)
            val shop =
                ShopViewModel(
                    Shopping(players, players),
                    GetProfile(players, players),
                    SetAvatar(players, players),
                    analytics,
                )

            shop.shown()
            testScheduler.advanceUntilIdle()

            assertEquals(
                GameError.ITEM_NOT_FOUND,
                shop.state.value.failure
                    ?.error,
            )
            assertEquals(null, shop.state.value.profile)
        }

    private class FakeShop :
        PlayerRepository,
        SessionRepository {
        var profile = PROFILE
        var refuse: KvizicException? = null
        var readFails: KvizicException? = null

        override suspend fun ensure(): String = profile.playerId

        override suspend fun profile(): Profile = profile

        override suspend fun setAvatar(avatarId: String): Profile =
            profile.copy(avatarId = avatarId).also {
                profile =
                    it
            }

        override suspend fun shop(): List<ShopItem> = readFails?.let { throw it } ?: ITEMS

        override suspend fun buy(itemId: String): Profile {
            refuse?.let { throw it }
            val price = ITEMS.first { it.id == itemId }.price
            return profile.copy(coins = profile.coins - price, items = profile.items + itemId).also { profile = it }
        }

        override suspend fun wear(
            frameId: String?,
            titleId: String?,
        ): Profile = profile.copy(frameId = frameId, titleId = titleId).also { profile = it }
    }

    private companion object {
        val ITEMS =
            listOf(
                ShopItem("frame.double", ShopItemKind.FRAME, 50),
                ShopItem("frame.dashes", ShopItemKind.FRAME, 100),
                ShopItem("frame.bulbs", ShopItemKind.FRAME, 200),
                ShopItem("title.lucky", ShopItemKind.TITLE, 30),
                ShopItem("eagle", ShopItemKind.AVATAR, 300),
            )

        val PROFILE =
            Profile(
                playerId = "p1",
                displayName = "Брзи Јеж",
                nameSource = NameSource.GENERATED,
                avatarId = "hedgehog",
                playGamesLinked = false,
                stats = PlayerStats(),
                coins = 120,
            )
    }
}
