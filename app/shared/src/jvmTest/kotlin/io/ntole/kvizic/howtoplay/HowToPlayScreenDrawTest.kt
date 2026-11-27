package io.ntole.kvizic.howtoplay

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.cutTexts
import io.ntole.kvizic.design.skin.Skin
import io.ntole.kvizic.design.skin.Skins
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.passTime
import io.ntole.kvizic.renderSettled
import io.ntole.kvizic.tap
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HowToPlayScreenDrawTest {
    @Test
    fun `every card is whole on a small phone at a large font in every skin and language`() {
        Language.entries.forEach { language ->
            val words = stringsOf(language).howToPlay
            val cards =
                listOf(
                    listOf(words.questionTitle, words.questionBody, words.next),
                    listOf(words.pointsTitle, words.pointsBody, words.next),
                    listOf(words.roomsTitle, words.roomsBody, words.play),
                )
            Skins.ALL.forEach { skin ->
                listOf(1f, LARGE_FONT).forEach { fontScale ->
                    cards.forEachIndexed { card, shown ->
                        val where = "$language, ${skin.id} at $fontScale, card $card"
                        val scene = scene(language, card, skin, fontScale)
                        try {
                            write("how-to-play-${skin.id}-$fontScale-${language.tag}-$card") { scene.renderSettled() }
                            assertTrue(scene.texts().containsAll(shown + words.skip), "$where: ${scene.texts()}")
                            assertEquals(emptyList(), scene.cutTexts(), "$where: cut")
                        } finally {
                            scene.close()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `Next steps through the cards, Play leaves from the last and Skip from any`() {
        val words = stringsOf(Language.DEFAULT).howToPlay
        var skipped = 0
        var played = 0
        val scene = scene(Language.DEFAULT, onSkip = { skipped++ }, onPlay = { played++ })
        try {
            scene.tap(words.next)
            scene.passTime(PAGE_TURN_MILLIS)
            assertTrue(words.pointsTitle in scene.texts(), "the second card")
            scene.tap(words.next)
            scene.passTime(PAGE_TURN_MILLIS)
            assertTrue(words.roomsTitle in scene.texts(), "the third card")
            scene.tap(words.play)
            scene.tap(words.skip)
        } finally {
            scene.close()
        }
        assertEquals(1 to 1, played to skipped)
    }

    private fun write(
        name: String,
        image: () -> Image,
    ) {
        val directory = System.getenv("KVIZIC_DESIGN_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        directory.mkdirs()
        File(directory, "$name.png").writeBytes(checkNotNull(image().encodeToData()).bytes)
    }

    private fun scene(
        language: Language,
        firstCard: Int = 0,
        skin: Skin = Skins.Default,
        fontScale: Float = 1f,
        onSkip: () -> Unit = {},
        onPlay: () -> Unit = {},
    ): ImageComposeScene =
        ImageComposeScene(width = SMALL_PHONE_WIDTH, height = SMALL_PHONE_HEIGHT, density = Density(1f, fontScale)) {
            GameTheme(language, skin) {
                HowToPlayScreen(onSkip = onSkip, onPlay = onPlay, firstCard = firstCard)
            }
        }.also { it.renderSettled() }

    private companion object {
        const val SMALL_PHONE_WIDTH = 360
        const val SMALL_PHONE_HEIGHT = 616
        const val LARGE_FONT = 1.3f
        const val PAGE_TURN_MILLIS = 1_000L
    }
}
