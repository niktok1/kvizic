package io.ntole.kvizic.shop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.core.domain.player.ShopItem
import io.ntole.kvizic.core.domain.player.ShopItemKind
import io.ntole.kvizic.cutTexts
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.design.avatar.AvatarArt
import io.ntole.kvizic.design.avatar.FrameArt
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.everyText
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.theme.GameTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The shop drawn off screen in each language, on a small phone and at a large font: every item named, nothing cut. */
class ShopScreenDrawTest {
    @Test
    fun `the shop names every item, says what is lacking and cuts no word`() {
        Language.entries.forEach { language ->
            val words = stringsOf(language).economy
            listOf(Density(1f), Density(1f, 1.3f)).forEach { density ->
                val where = "$language at ${density.fontScale}"
                val scene =
                    ImageComposeScene(width = 360, height = 640, density = density) {
                        GameTheme(language) { Stage(Modifier.fillMaxSize()) { ShopScreen(STATE, ShopActions()) } }
                    }
                try {
                    val still = scene.renderSettled()
                    System.getenv("KVIZIC_DESIGN_DIR")?.takeIf { it.isNotBlank() }?.let(::File)?.let { directory ->
                        directory.mkdirs()
                        File(directory, "shop-${language.name.lowercase()}-${density.fontScale}.png")
                            .writeBytes(checkNotNull(still.encodeToData()).bytes)
                    }
                    val missing = words.missing.fill(words.coins.of(30, language))
                    listOf(words.frames, words.howToEarn, missing).forEach {
                        assertTrue(it in scene.everyText(), "$where: \"$it\" is not shown")
                    }
                    val said = scene.descriptions().joinToString("\n")
                    (words.frameNames + words.animalNames).forEach { assertTrue(it in said, "$where: $it is not said") }
                    assertEquals(emptyList(), scene.cutTexts(), "$where: cut")
                } finally {
                    scene.close()
                }
            }
        }
    }

    private companion object {
        val TITLE = mapOf("sr-Cyrl" to "Енциклопедија", "hr" to "Enciklopedija", "en" to "Encyclopedia")

        val ITEMS =
            FrameArt.DRAWN.mapIndexed { i, id -> ShopItem(id, ShopItemKind.FRAME, 50 + 150 * i) } +
                ShopItem("title.encyclopedia", ShopItemKind.TITLE, 120, TITLE) +
                AvatarArt.SHOP.map { ShopItem(it, ShopItemKind.AVATAR, 400) }

        val STATE =
            ShopState(
                items = ITEMS,
                profile =
                    Profile(
                        playerId = "p1",
                        displayName = "Брзи Јеж",
                        nameSource = NameSource.GENERATED,
                        avatarId = "hedgehog",
                        playGamesLinked = false,
                        stats = PlayerStats(),
                        coins = 170,
                        items = listOf(FrameArt.DRAWN.first(), "title.encyclopedia"),
                        frameId = FrameArt.DRAWN.first(),
                        titleId = "title.encyclopedia",
                    ),
                selected = FrameArt.DRAWN[1],
            )
    }
}
