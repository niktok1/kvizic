package io.ntole.kvizic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.component.Wordmark
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.stringsOf
import io.ntole.kvizic.theme.GameTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Google Play's feature graphic, 1024 by 500: the game's sign on the stage, drawn by the design system
 * itself so it is the sign the app shows. With `KVIZIC_DESIGN_DIR` set, written there as a PNG to upload.
 */
class StoreArtTest {
    @Test
    fun `the feature graphic is the sign whole on the stage`() {
        val name = stringsOf(Language.DEFAULT).gameName
        val scene =
            ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(DENSITY)) {
                GameTheme(Language.DEFAULT) {
                    Stage(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Wordmark(name, Modifier.width(SIGN_WIDTH))
                        }
                    }
                }
            }
        try {
            val image = scene.renderSettled()
            val sign = scene.nodes().single { name in it.texts }.boundsInRoot
            assertTrue(
                sign.left >= 0f && sign.top >= 0f && sign.right <= WIDTH && sign.bottom <= HEIGHT,
                "the sign runs off the graphic: $sign",
            )
            System.getenv("KVIZIC_DESIGN_DIR")?.takeIf { it.isNotBlank() }?.let(::File)?.let { directory ->
                directory.mkdirs()
                File(directory, "store-feature-graphic.png").writeBytes(checkNotNull(image.encodeToData()).bytes)
            }
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val WIDTH = 1024
        const val HEIGHT = 500

        /** Drawn at a phone's density, so the sign's bulbs and type are as sharp as on one. */
        const val DENSITY = 2.5f

        val SIGN_WIDTH = 330.dp
    }
}
