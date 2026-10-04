package io.ntole.kvizic.admin

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import io.ntole.kvizic.core.domain.moderation.AccountDetail
import io.ntole.kvizic.core.domain.moderation.AccountGame
import io.ntole.kvizic.core.domain.moderation.AccountSummary
import io.ntole.kvizic.core.domain.moderation.AccountTopicStat
import io.ntole.kvizic.core.domain.moderation.BankOverview
import io.ntole.kvizic.core.domain.moderation.BankStatus
import io.ntole.kvizic.core.domain.moderation.ReportedQuestion
import io.ntole.kvizic.core.domain.moderation.SoloBest
import io.ntole.kvizic.core.domain.report.QuestionReportReason
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import io.ntole.kvizic.design.skin.KvizicSkin
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every tab of the moderation app drawn off screen at a desktop window's size, its texts and taps read as a
 * screen reader reads them; with `KVIZIC_DESIGN_DIR` set each is written there as a PNG to look at.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreensDrawTest {
    private val asked = mutableListOf<String>()

    /** Records what each tap asks for, by name. */
    private val actions =
        object : ModerationActions {
            override fun unlock() {
                asked += "unlock"
            }

            override fun approveCurrent() {
                asked += "approve"
            }

            override fun startReject() {
                asked += "reject"
            }

            override fun select(tab: AdminTab) {
                asked += "tab ${tab.name}"
            }

            override fun deleteAccount() {
                asked += "delete"
            }
        }

    private val unlocked =
        ModerationState(
            unlocked = true,
            topics = listOf(Topic("GEOGRAPHY", "Географија", "Geography", 6), Topic("SPORT", "Спорт", "Sport", 6)),
        )

    @Test
    fun `locked it asks for the token and names the server, production's loudly`() {
        val texts = draw("locked", ModerationState(tokenText = SecretText("abc")), KvizicEnvironment.PROD)
        assertTrue("Prod · https://kvizic-api.ntole.com" in texts, texts.toString())
        assertTrue("abc" !in texts, "the token shows")
        tapIn("locked", ModerationState(tokenText = SecretText("abc")), "Unlock")
        assertEquals(listOf("unlock"), asked)
    }

    @Test
    fun `review shows the draft whole and its keys`() {
        val state =
            unlocked.copy(
                review = ReviewState(loaded = true, drafts = listOf(question("q1"), question("q2")), decided = 3),
            )
        val texts = draw("review", state)
        listOf(
            "Која река протиче кроз Нови Сад?",
            "Дунав · right",
            "Сава",
            "Географија",
            "1 of 2 · decided 3",
            "Approve  [A]",
            "Review (2)",
        ).forEach { assertTrue(it in texts, "no \"$it\" in $texts") }
        assertTrue(texts.any { "70% right of 10" in it && "right in 4.2 s" in it }, texts.toString())
        assertTrue(texts.any { "2 silent" in it && "plays medium" in it }, "an easy question that plays medium")
        tapIn("review", state, "Approve  [A]")
        tapIn("review", state, "Bank")
        assertEquals(listOf("approve", "tab BANK"), asked)
    }

    @Test
    fun `a rejection offers the ready reasons`() {
        val state =
            unlocked.copy(review = ReviewState(loaded = true, drafts = listOf(question("q1")), rejecting = true))
        val texts = draw("review-rejecting", state)
        READY_REASONS.forEach { assertTrue(it in texts, "no \"$it\"") }
    }

    @Test
    fun `the bank lists each question with what can be done where it stands`() {
        val state =
            unlocked.copy(
                tab = AdminTab.BANK,
                bank =
                    BankState(
                        loaded = true,
                        questions = listOf(question("q1", BankStatus.APPROVED), question("q3", BankStatus.SUSPENDED)),
                        next = "c",
                    ),
            )
        val texts = draw("bank", state)
        // A suspended question can go either way: out for good, or back in play.
        assertEquals(2, texts.count { it == "Retire" }, texts.toString())
        assertEquals(1, texts.count { it == "Restore" })
    }

    @Test
    fun `reports show their reasons and outcomes`() {
        val reported =
            ReportedQuestion(
                question("q1", BankStatus.SUSPENDED),
                open = 3,
                reasons = listOf(QuestionReportReason.WRONG_ANSWER to 2, null to 1),
                lastReportedAt = 1,
            )
        val state =
            unlocked.copy(tab = AdminTab.REPORTS, reports = ReportsState(loaded = true, reported = listOf(reported)))
        val texts = draw("reports", state)
        listOf("3 open", "Wrong answer 2", "Unknown reason 1", "Mark fixed", "Dismiss", "Retire").forEach {
            assertTrue(it in texts, "no \"$it\" in $texts")
        }
    }

    @Test
    fun `the overview counts the bank and the live games`() {
        val overview =
            BankOverview(
                byStatus = mapOf(BankStatus.APPROVED to 40, BankStatus.DRAFT to 8),
                approvedByTopic = mapOf("GEOGRAPHY" to 6),
                liveLobbies = 2,
                liveGames = 1,
                connectedPlayers = 5,
                gamesToday = 9,
            )
        val texts = draw("overview", unlocked.copy(tab = AdminTab.OVERVIEW, overview = overview))
        listOf("Approved 40", "Draft 8", "Географија 6", "Спорт 0").forEach { assertTrue(it in texts, "no \"$it\"") }
    }

    @Test
    fun `an account is deleted from the dialog alone`() {
        val state =
            unlocked.copy(
                tab = AdminTab.ACCOUNTS,
                accounts = AccountsState(accountId = "p1", confirming = true),
            )
        val texts = draw("accounts", state)
        assertTrue("Delete account p1?" in texts, texts.toString())
        tapIn("accounts", state, "Delete account")
        assertEquals(listOf("delete"), asked)
    }

    @Test
    fun `the accounts tab lists the players with their level, stats and when they were seen`() {
        val fox =
            AccountSummary(
                id = "p1",
                name = "Лукави Лисац",
                avatarId = "fox",
                playGamesLinked = true,
                level = 7,
                xp = 400,
                gamesPlayed = 12,
                gamesWon = 4,
                answersGiven = 140,
                answersCorrect = 100,
                soloRuns = 3,
                soloBest = SoloBest(medium = 410),
                createdAt = 1_000L,
                lastSeenAt = null,
            )
        val state =
            unlocked.copy(
                tab = AdminTab.ACCOUNTS,
                accounts = AccountsState(loaded = true, players = listOf(fox), total = 1),
            )
        val texts = draw("accounts-list", state)
        listOf("Level 7", "Play Games", "12 games, 4 won · 71% right of 140 answers").forEach {
            assertTrue(it in texts, "no \"$it\": $texts")
        }
        assertTrue("1 players" in texts, texts.toString())
        assertTrue("Лукави Лисац" in texts, texts.toString())
        listOf("Last seen", "Newest", "Level", "Games").forEach { assertTrue(it in texts, "no \"$it\"") }
    }

    @Test
    fun `an opened account shows its topics and games and offers its deletion`() {
        val fox =
            AccountSummary(
                id = "p1",
                name = "Лукави Лисац",
                avatarId = "fox",
                playGamesLinked = false,
                level = 2,
                xp = 20,
                gamesPlayed = 1,
                gamesWon = 0,
                answersGiven = 10,
                answersCorrect = 7,
                soloRuns = 0,
                soloBest = SoloBest(),
                createdAt = 1_000L,
                lastSeenAt = 2_000L,
            )
        val detail =
            AccountDetail(
                account = fox,
                topics = listOf(AccountTopicStat("SPORT", answered = 10, correct = 7)),
                recentGames = listOf(AccountGame(2_000L, false, 4, 2, 320, 7, 10, true)),
            )
        val state =
            unlocked.copy(
                tab = AdminTab.ACCOUNTS,
                accounts = AccountsState(loaded = true, players = listOf(fox), total = 1, selected = detail),
            )
        val texts = draw("accounts-detail", state)
        assertTrue("Guest" in texts, texts.toString())
        assertTrue("Спорт 7/10" in texts, texts.toString())
        assertTrue(texts.any { it.endsWith("2nd of 4, 320 points, 7/10 right") }, texts.toString())
        assertTrue(texts.any { it.startsWith("Created 1970-01-01 00:00 UTC") }, texts.toString())
    }

    @Test
    fun `the editor names what is wrong before anything is sent`() {
        val editor = EditorState.of(question("q1")).copy(options = listOf("Дунав", "дунав"))
        val texts = draw("editor", unlocked.copy(editor = editor))
        assertTrue("Two answers are the same." in texts, texts.toString())
        assertTrue("Changing the answers or which is right starts its play counts afresh." in texts)
    }

    private fun scene(
        state: ModerationState,
        environment: KvizicEnvironment,
    ): ImageComposeScene =
        ImageComposeScene(width = WIDTH, height = HEIGHT, density = Density(1f)) {
            Screen(state, environment)
        }

    @Composable
    private fun Screen(
        state: ModerationState,
        environment: KvizicEnvironment,
    ) {
        KvizicSkin { ModerationScreen(state, environment, actions) }
    }

    /** Draws [state], writes it when asked, and returns every text it shows, a field's label included. */
    private fun draw(
        name: String,
        state: ModerationState,
        environment: KvizicEnvironment = KvizicEnvironment.DEV,
    ): List<String> {
        val scene = scene(state, environment)
        try {
            val image = scene.render()
            repeat(2) { scene.render().close() }
            System.getenv("KVIZIC_DESIGN_DIR")?.takeIf { it.isNotBlank() }?.let { dir ->
                File(dir).mkdirs()
                File(dir, "admin-$name.png").writeBytes(checkNotNull(image.encodeToData()).bytes)
            }
            image.close()
            return scene.semanticsOwners
                .flatMap { it.getAllSemanticsNodes(mergingEnabled = false) }
                .flatMap { node ->
                    node.config
                        .getOrNull(SemanticsProperties.Text)
                        .orEmpty()
                        .map { it.text } +
                        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                }
        } finally {
            scene.close()
        }
    }

    private fun tapIn(
        name: String,
        state: ModerationState,
        text: String,
    ) {
        val scene = scene(state, KvizicEnvironment.DEV)
        try {
            scene.render().close()
            val node =
                scene.semanticsOwners
                    .flatMap { it.getAllSemanticsNodes(mergingEnabled = true) }
                    .single { n ->
                        n.config
                            .getOrNull(SemanticsProperties.Text)
                            .orEmpty()
                            .any { it.text == text }
                    }
            val tap =
                checkNotNull(node.config.getOrNull(SemanticsActions.OnClick)?.action) { "$name: $text takes no tap" }
            tap()
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val WIDTH = 1280
        const val HEIGHT = 860
    }
}
