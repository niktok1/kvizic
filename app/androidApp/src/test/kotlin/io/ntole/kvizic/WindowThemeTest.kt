package io.ntole.kvizic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.ntole.kvizic.design.skin.Skins
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The window behind the app, and Android 12's splash screen, are the page background, which Compose draws
 * over them from its first frame: read from this module's resources, whose colour is a platform copy of the
 * theme's, held equal here, so a theme's change fails this until the copy changes too.
 */
class WindowThemeTest {
    @Test
    fun theWindowColourIsThePageBackground() {
        assertEquals(hexOf(Skins.Default.colors.page), colour(PAGE_BACKGROUND))
    }

    @Test
    fun theAppStartsInTheWindowTheme() {
        val application = parse(File(MAIN, "AndroidManifest.xml")).getElementsByTagName("application").item(0)
        assertEquals("@style/Theme.Kvizic", (application as Element).getAttribute("android:theme"))
    }

    @Test
    fun theWindowIsThePageBackground() {
        assertEquals("@color/$PAGE_BACKGROUND", item("values", "Base.Theme.Kvizic", "android:windowBackground"))
    }

    @Test
    fun theSplashScreenIsThePageBackgroundAndTheLauncherIcon() {
        val splash = style("values-v31", "Theme.Kvizic")
        assertEquals("Base.Theme.Kvizic", splash.getAttribute("parent"))
        assertEquals(
            "@color/$PAGE_BACKGROUND",
            item("values-v31", "Theme.Kvizic", "android:windowSplashScreenBackground"),
        )
        assertEquals(
            "@mipmap/ic_launcher",
            item("values-v31", "Theme.Kvizic", "android:windowSplashScreenAnimatedIcon"),
        )
    }

    private fun hexOf(colour: Color): String = "#%08X".format(colour.toArgb())

    /** A colour resource as `#AARRGGBB`, a six-digit one opaque. */
    private fun colour(name: String): String {
        val value =
            elements(File(RES, "values/colors.xml"), "color")
                .single { it.getAttribute("name") == name }
                .textContent
                .trim()
                .uppercase()
        return if (value.length == 7) "#FF${value.drop(1)}" else value
    }

    private fun style(
        directory: String,
        name: String,
    ): Element = elements(File(RES, "$directory/themes.xml"), "style").single { it.getAttribute("name") == name }

    private fun item(
        directory: String,
        styleName: String,
        itemName: String,
    ): String =
        style(directory, styleName)
            .getElementsByTagName("item")
            .let { items -> (0 until items.length).map { items.item(it) as Element } }
            .single { it.getAttribute("name") == itemName }
            .textContent
            .trim()

    private fun elements(
        file: File,
        tag: String,
    ): List<Element> {
        val nodes = parse(file).getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun parse(file: File) =
        DocumentBuilderFactory
            .newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement

    private companion object {
        const val PAGE_BACKGROUND = "kvizic_page_background"

        // A unit test runs in its module's directory.
        val MAIN = File("src/main")
        val RES = File(MAIN, "res")
    }
}
