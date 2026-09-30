package io.ntole.kvizic.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.RecordingClipboard
import io.ntole.kvizic.RecordingUris
import io.ntole.kvizic.about.AboutScreen
import io.ntole.kvizic.about.AppVersion
import io.ntole.kvizic.about.DeleteAccountButton
import io.ntole.kvizic.about.Deletion
import io.ntole.kvizic.core.domain.analytics.AnalyticsEvent
import io.ntole.kvizic.core.domain.analytics.AnalyticsProperty
import io.ntole.kvizic.core.domain.error.CoreError
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.home.HomeFailure
import io.ntole.kvizic.home.HomeScreen
import io.ntole.kvizic.home.HomeState
import io.ntole.kvizic.language.KvizicStrings
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.navigation.BackTopBar
import io.ntole.kvizic.nodes
import io.ntole.kvizic.settle
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.ShellTheme
import io.ntole.kvizic.update.UpdateButton
import io.ntole.kvizic.update.UpdateScreen
import io.ntole.kvizic.update.UpdateWay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Every tap on every screen is reported, by a stable name: each screen is drawn off screen in the states
 * that show all it can be tapped on, and everything a screen reader could tap, but a text field, is
 * tapped, and must report exactly one [AnalyticsEvent.TAP], whose element is named `screen.what`. So a
 * button added later without [tapped] fails here.
 *
 * The names are listed, screen by screen, since a name once sent never changes: a dashboard built on it
 * would lose it.
 */
class TapsTest {
    private val analytics = RecordingAnalytics()

    /** Every URL a tap asked to open: nothing here reaches a browser. */
    private val uris = RecordingUris()

    @Test
    fun `every tap on Home and the top bar is reported`() {
        assertEquals(
            setOf("home.about"),
            elementsTapped { HomeScreen(HomeState(profile = PROFILE), onAbout = {}, onRetry = {}) },
        )
        assertEquals(
            setOf("home.try_again", "home.about"),
            elementsTapped {
                HomeScreen(HomeState(failure = HomeFailure(CoreError.NETWORK)), onAbout = {}, onRetry = {})
            },
        )
        assertEquals(setOf("top_bar.back"), elementsTapped { BackTopBar(onBack = {}) })
    }

    /** Each of the site's pages, and a licence's text: opened by the test's own handler, never a browser. */
    @Test
    fun `every tap on the About screen is reported`() {
        assertEquals(
            setOf(
                "about.privacy",
                "about.terms",
                "about.deletion_page",
                "about.contact",
                "about.copy_account_id",
                "about.statistics",
                "about.statistics_info",
                "about.statistics_info_ok",
                "about.licence",
                // Deleting the account, then its dialog's two buttons.
                "about.delete_account",
                "about.delete_account_confirm",
                "about.delete_account_cancel",
            ),
            elementsTapped {
                @Suppress("DEPRECATION")
                CompositionLocalProvider(LocalClipboardManager provides RecordingClipboard()) {
                    AboutScreen(AppVersion("0.1.0", 100), accountId = "p1") {
                        DeleteAccountButton(deletion = Deletion.Idle, onDelete = {})
                    }
                }
            },
        )
        assertTrue(uris.opened.isNotEmpty())
    }

    @Test
    fun `every tap on the update screen is reported`() {
        assertEquals(setOf("update.store"), elementsTapped { UpdateScreen(UpdateButton(UpdateWay.STORE) {}) })
        assertEquals(setOf("update.reload"), elementsTapped { UpdateScreen(UpdateButton(UpdateWay.RELOAD) {}) })
    }

    private fun elementsTapped(content: @Composable () -> Unit): Set<String> {
        val scene =
            ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
                CompositionLocalProvider(LocalAnalytics provides analytics, LocalUriHandler provides uris) {
                    ShellTheme { KvizicStrings(Language.DEFAULT) { content() } }
                }
            }
        val reported = mutableSetOf<String>()
        val done = mutableSetOf<String>()
        try {
            scene.settle()
            // Twice: what the first taps bring up is tapped too.
            repeat(2) {
                scene.tappable().filter { signatureOf(it) !in done }.forEach { node ->
                    done += signatureOf(node)
                    val before = analytics.named(AnalyticsEvent.TAP).size
                    val tap = assertNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action, signatureOf(node))
                    tap()
                    scene.settle()
                    val taps = analytics.named(AnalyticsEvent.TAP).drop(before)
                    assertEquals(1, taps.size, "a tap on ${signatureOf(node)} reported $taps")
                    val element = taps.single().properties[AnalyticsProperty.ELEMENT] as? String
                    assertTrue(element != null && ELEMENT_NAME.matches(element), "\"$element\" is no element's name")
                    reported += element
                }
            }
        } finally {
            scene.close()
        }
        return reported
    }

    /** Everything a player could tap: whatever takes a click and is on, but a text field. */
    private fun ImageComposeScene.tappable(): List<SemanticsNode> =
        nodes().filter { node ->
            node.config.getOrNull(SemanticsActions.OnClick)?.action != null &&
                node.config.getOrNull(SemanticsProperties.Disabled) == null &&
                node.config.getOrNull(SemanticsActions.SetText) == null
        }

    private fun signatureOf(node: SemanticsNode): String = "${node.texts}${node.descriptions}@${node.positionInRoot}"

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 900

        /** `screen.what`, in lower case and underscores. */
        val ELEMENT_NAME = Regex("[a-z_]+\\.[a-z_]+")

        val PROFILE =
            Profile(
                playerId = "p1",
                displayName = "Брзи Јеж",
                nameSource = NameSource.GENERATED,
                avatarId = "hedgehog",
                playGamesLinked = false,
                stats = PlayerStats(),
            )
    }
}
