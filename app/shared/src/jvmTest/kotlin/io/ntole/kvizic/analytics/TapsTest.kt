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
import io.ntole.kvizic.core.domain.lobby.GamePhase
import io.ntole.kvizic.core.domain.lobby.LobbyExit
import io.ntole.kvizic.core.domain.lobby.LobbyKind
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.PublicLobbies
import io.ntole.kvizic.core.domain.player.NameSource
import io.ntole.kvizic.core.domain.player.PlayerStats
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.core.domain.topic.TopicGroup
import io.ntole.kvizic.descriptions
import io.ntole.kvizic.home.HomeActions
import io.ntole.kvizic.home.HomeFailure
import io.ntole.kvizic.home.HomeScreen
import io.ntole.kvizic.home.HomeState
import io.ntole.kvizic.home.ProfileScreen
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.navigation.BackTopBar
import io.ntole.kvizic.nodes
import io.ntole.kvizic.room.Entry
import io.ntole.kvizic.room.EntryWay
import io.ntole.kvizic.room.JoinScreen
import io.ntole.kvizic.room.MEMBERS
import io.ntole.kvizic.room.PublicRoomsScreen
import io.ntole.kvizic.room.RESULTS
import io.ntole.kvizic.room.RoomActions
import io.ntole.kvizic.room.RoomScreen
import io.ntole.kvizic.room.SettingsScreen
import io.ntole.kvizic.room.TOPICS
import io.ntole.kvizic.room.YOU
import io.ntole.kvizic.room.answering
import io.ntole.kvizic.room.inLobby
import io.ntole.kvizic.room.lobby
import io.ntole.kvizic.room.revealing
import io.ntole.kvizic.settle
import io.ntole.kvizic.texts
import io.ntole.kvizic.theme.GameTheme
import io.ntole.kvizic.update.UpdateButton
import io.ntole.kvizic.update.UpdateScreen
import io.ntole.kvizic.update.UpdateWay
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val buttons =
            setOf(
                "home.about",
                "home.quick_play",
                "home.create_room",
                "home.join_by_code",
                "home.public_rooms",
                "home.solo",
            )
        // The player's own line opens their profile, once there is a player to show.
        assertEquals(
            buttons + "home.profile",
            elementsTapped { HomeScreen(HomeState(profile = PROFILE), HomeActions()) },
        )
        assertEquals(
            buttons + "home.try_again",
            elementsTapped { HomeScreen(HomeState(failure = HomeFailure(CoreError.NETWORK)), HomeActions()) },
        )
        assertEquals(
            buttons + "home.profile" + "home.exit_ok" + "home.failure_ok",
            elementsTapped {
                HomeScreen(
                    HomeState(profile = PROFILE),
                    HomeActions(),
                    entry = Entry.Failed(EntryWay.QUICK_PLAY, CoreError.NETWORK),
                    exit = LobbyExit.KICKED,
                )
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
    fun `every tap in the room is reported`() {
        // A member votes a player out from their seat...
        val member = inLobby(GamePhase.Waiting(null))
        assertEquals(
            setOf(
                "top_bar.back",
                "room.copy_code",
                "room.share",
                "room.reaction",
                "room.seat",
                "room.vote_cancel",
                "room.vote_kick",
                "room.leave_cancel",
                "room.leave_confirm",
            ),
            elementsTapped { RoomScreen(member, TOPICS, note = null, bursts = emptyMap(), actions = RoomActions()) },
        )
        // ...or takes back a vote they gave: here, whichever seat is tapped.
        val voted = MEMBERS.map { it.copy(kickVotes = 1, kickVotesNeeded = 3, kickVotedByYou = it.playerId != YOU) }
        val voter = inLobby(GamePhase.Waiting(null), lobby = lobby(members = voted))
        assertTrue(
            "room.withdraw_vote" in
                elementsTapped { RoomScreen(voter, TOPICS, note = null, bursts = emptyMap(), actions = RoomActions()) },
        )
        val host = inLobby(GamePhase.Waiting(null), lobby = lobby(host = YOU))
        assertEquals(
            setOf(
                "top_bar.back",
                "room.copy_code",
                "room.share",
                "room.reaction",
                "room.settings",
                "room.start",
                "room.seat",
                "room.member_cancel",
                "room.make_host",
                "room.kick",
                "room.leave_cancel",
                "room.leave_confirm",
            ),
            elementsTapped { RoomScreen(host, TOPICS, note = null, bursts = emptyMap(), actions = RoomActions()) },
        )
        val alone = MEMBERS.filter { it.playerId == YOU }
        val solo = inLobby(GamePhase.Waiting(null), lobby = lobby(alone, host = YOU, kind = LobbyKind.SOLO))
        assertEquals(
            setOf("top_bar.back", "room.difficulty", "room.start", "room.leave_cancel", "room.leave_confirm"),
            elementsTapped { RoomScreen(solo, TOPICS, note = null, bursts = emptyMap(), actions = RoomActions()) },
        )
        assertEquals(
            // Leaving mid-game asks first, as anywhere in the room.
            setOf("question.leave", "question.answer", "room.leave_cancel", "room.leave_confirm"),
            elementsTapped {
                RoomScreen(
                    inLobby(answering()),
                    TOPICS,
                    note = null,
                    bursts = emptyMap(),
                    actions = RoomActions(),
                )
            },
        )
        assertEquals(
            setOf(
                "question.leave",
                "reveal.report",
                "report.reason",
                "report.cancel",
                "room.leave_cancel",
                "room.leave_confirm",
            ),
            elementsTapped {
                RoomScreen(
                    inLobby(revealing()),
                    TOPICS,
                    note = null,
                    bursts = emptyMap(),
                    actions = RoomActions(),
                )
            },
        )
        val members = MEMBERS.map { if (it.playerId == YOU) it.copy(onResults = true) else it }
        assertEquals(
            setOf("results.back_to_room", "results.leave"),
            elementsTapped {
                RoomScreen(
                    inLobby(GamePhase.Waiting(RESULTS), lobby = lobby(members = members)),
                    TOPICS,
                    note = null,
                    bursts = emptyMap(),
                    actions = RoomActions(),
                )
            },
        )
    }

    @Test
    fun `every tap on joining, the public rooms and the settings is reported`() {
        assertEquals(
            setOf("join.digit"),
            elementsTapped { JoinScreen("", onCode = {}, onJoin = {}, entry = Entry.None, onDismissFailure = {}) },
        )
        assertEquals(
            setOf("join.delete", "join.join"),
            elementsTapped {
                JoinScreen(
                    "482915",
                    onCode = {},
                    onJoin = {},
                    entry = Entry.None,
                    onDismissFailure = {},
                )
            },
        )
        assertEquals(
            setOf("public_rooms.quick_play", "public_rooms.create_room"),
            elementsTapped {
                PublicRoomsScreen(PublicLobbies(emptyList(), 3, 1), null, Entry.None, {}, {}, {}, {})
            },
        )
        assertEquals(
            setOf(
                "settings.questions",
                "settings.time",
                "settings.topics",
                "settings.difficulty",
                "settings.players",
                "settings.visibility",
                "settings.penalty",
                "settings.done",
                // The topics' picker, which the topics' line opens in the settings' place.
                "top_bar.back",
                "topics.all",
                "topics.group",
                "topics.whole_group",
                "topics.topic",
                "topics.done",
            ),
            elementsTapped {
                SettingsScreen(LobbySettings(), TOPICS, onChange = {}, doneLabel = "OK", onDone = {}, groups = GROUPS)
            },
        )
    }

    @Test
    fun `every tap on the profile is reported`() {
        assertEquals(
            setOf("profile.avatar"),
            elementsTapped {
                ProfileScreen(HomeState(profile = PROFILE), TOPICS, onPick = {})
            },
        )
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
                    GameTheme(Language.DEFAULT) { content() }
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
                    val actions =
                        listOfNotNull(
                            node.config.getOrNull(SemanticsActions.OnClick)?.action,
                            node.config.getOrNull(SemanticsActions.OnLongClick)?.action,
                        )
                    actions.forEach { tap ->
                        val before = analytics.named(AnalyticsEvent.TAP).size
                        tap()
                        scene.settle()
                        val taps = analytics.named(AnalyticsEvent.TAP).drop(before)
                        assertEquals(1, taps.size, "a tap on ${signatureOf(node)} reported $taps")
                        val element = taps.single().properties[AnalyticsProperty.ELEMENT] as? String
                        assertTrue(
                            element != null && ELEMENT_NAME.matches(element),
                            "\"$element\" is no element's name",
                        )
                        reported += element
                    }
                }
            }
        } finally {
            scene.close()
        }
        return reported
    }

    /** Everything a player could tap or hold: whatever takes a click or a long one and is on, but a text field. */
    private fun ImageComposeScene.tappable(): List<SemanticsNode> =
        nodes().filter { node ->
            (
                node.config.getOrNull(SemanticsActions.OnClick)?.action != null ||
                    node.config.getOrNull(SemanticsActions.OnLongClick)?.action != null
            ) &&
                node.config.getOrNull(SemanticsProperties.Disabled) == null &&
                node.config.getOrNull(SemanticsActions.SetText) == null
        }

    private fun signatureOf(node: SemanticsNode): String = "${node.texts}${node.descriptions}@${node.positionInRoot}"

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 900

        /** `screen.what`, in lower case and underscores. */
        val ELEMENT_NAME = Regex("[a-z_]+\\.[a-z_]+")

        val GROUPS = listOf(TopicGroup("KNOWLEDGE", "Знање", "Knowledge"))

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
