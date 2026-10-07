package io.ntole.kvizic.admin

import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ntole.kvizic.core.domain.moderation.AccountOrder
import io.ntole.kvizic.core.domain.moderation.BankOverview
import io.ntole.kvizic.core.domain.moderation.BankStatus
import io.ntole.kvizic.core.domain.moderation.ModeratedQuestion
import io.ntole.kvizic.core.domain.moderation.QuestionRules
import io.ntole.kvizic.core.domain.moderation.ReportOutcome
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.StageDialog
import io.ntole.kvizic.design.component.TextInput
import io.ntole.kvizic.design.skin.KvizicTheme
import kotlin.time.Clock

/**
 * The moderation app, whole: which server it talks to, the token, the tabs, and the tab shown, or the
 * editor in its place while a question is edited.
 */
@Composable
fun ModerationScreen(
    state: ModerationState,
    environment: KvizicEnvironment,
    actions: ModerationActions,
    modifier: Modifier = Modifier,
) {
    val space = KvizicTheme.space
    Stage(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(space.lg),
            verticalArrangement = Arrangement.spacedBy(space.md),
        ) {
            Header(state, environment, actions)
            if (state.unlocked) {
                Tabs(state, actions)
                state.failure?.let { failure ->
                    KvizicText(failure, style = KvizicTheme.type.bodyStrong, color = KvizicTheme.colors.loss)
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val editor = state.editor
                    when {
                        editor != null -> {
                            Editor(editor, state, actions)
                        }

                        else -> {
                            when (state.tab) {
                                AdminTab.REVIEW -> Review(state, actions)
                                AdminTab.BANK -> Bank(state, actions)
                                AdminTab.REPORTS -> Reports(state, actions)
                                AdminTab.OVERVIEW -> Overview(state.overview, state)
                                AdminTab.ACCOUNTS -> Accounts(state, actions)
                            }
                        }
                    }
                }
            } else {
                state.failure?.let { failure ->
                    KvizicText(failure, style = KvizicTheme.type.bodyStrong, color = KvizicTheme.colors.loss)
                }
            }
        }
    }
}

/** The server the app talks to, production's in the loss colour, and the token's field or Lock. */
@Composable
private fun Header(
    state: ModerationState,
    environment: KvizicEnvironment,
    actions: ModerationActions,
) {
    val space = KvizicTheme.space
    val colors = KvizicTheme.colors
    val type = KvizicTheme.type
    val title: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            KvizicText("Kvizić moderation", style = type.headline)
            KvizicText(
                serverLineOf(environment),
                style = type.bodyStrong,
                color = if (environment == KvizicEnvironment.PROD) colors.loss else colors.onPageMuted,
            )
        }
    }
    // The field gives up the focus before it goes: on the web a field removed while focused leaves the
    // page's focus outside the app, and the review's keys would go nowhere until a click.
    val focusManager = LocalFocusManager.current
    val unlock = {
        focusManager.clearFocus()
        actions.unlock()
    }
    val tokenField: @Composable (Modifier) -> Unit = { modifier ->
        // Made anew on every Lock, so its undo history never gives the token back.
        key(state.locks) {
            TextInput(
                value = state.tokenText.value,
                onValueChange = actions::typeToken,
                label = "Admin token",
                modifier = modifier,
                secret = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { unlock() }),
            )
        }
    }
    val unlockButton: @Composable () -> Unit = {
        StageButton(
            "Unlock",
            onClick = unlock,
            size = ButtonSize.SMALL,
            enabled = state.tokenText.value.isNotBlank(),
        )
    }
    BoxWithConstraints {
        val narrow = maxWidth < NARROW_WIDTH
        when {
            state.unlocked -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.md),
                ) {
                    title(Modifier.weight(1f))
                    StageButton("Lock", onClick = actions::lock, kind = ButtonKind.SECONDARY, size = ButtonSize.SMALL)
                }
            }

            // A phone: the title, and the token's field and Unlock under it, across the width.
            narrow -> {
                Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                    title(Modifier)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(space.md),
                    ) {
                        tokenField(Modifier.weight(1f))
                        unlockButton()
                    }
                }
            }

            else -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.md),
                ) {
                    title(Modifier.weight(1f))
                    tokenField(Modifier.width(space.contentWidth))
                    unlockButton()
                }
            }
        }
    }
}

/** Narrower than this a window is a phone's: the tabs scroll and the review stacks. */
private val NARROW_WIDTH: Dp = 600.dp

/** The name and address of the server the moderator acts on. */
internal fun serverLineOf(environment: KvizicEnvironment): String =
    "${environment.displayName} · ${environment.apiBaseUrl}"

@Composable
private fun Tabs(
    state: ModerationState,
    actions: ModerationActions,
) {
    val space = KvizicTheme.space
    val chips: @Composable () -> Unit = {
        AdminTab.entries.forEach { tab ->
            val count =
                when (tab) {
                    AdminTab.REVIEW -> {
                        state.review.drafts.size
                            .takeIf { state.review.loaded }
                    }

                    AdminTab.REPORTS -> {
                        state.reports.reported.size
                            .takeIf { state.reports.loaded }
                    }

                    else -> {
                        null
                    }
                }
            val more = if (tab == AdminTab.REVIEW && state.review.more) "+" else ""
            Chip(
                if (count == null) tab.word() else "${tab.word()} ($count$more)",
                selected = tab == state.tab,
                tone = if (tab == state.tab) ChipTone.ACCENT else ChipTone.NEUTRAL,
                onClick = { actions.select(tab) },
            )
        }
    }
    val working: @Composable () -> Unit = {
        if (state.busy) {
            KvizicText("Working…", style = KvizicTheme.type.caption, color = KvizicTheme.colors.onPageMuted)
        }
        StageButton(
            "Load again",
            onClick = { actions.load(state.tab) },
            kind = ButtonKind.QUIET,
            size = ButtonSize.SMALL,
            enabled = !state.busy && state.editor == null,
        )
    }
    BoxWithConstraints {
        if (maxWidth < NARROW_WIDTH) {
            // A phone: the tabs scroll sideways, and Load again stands under them at the end.
            Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) { chips() }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) { working() }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chips()
                Spacer(Modifier.weight(1f))
                working()
            }
        }
    }
}

/**
 * The drafts one at a time, the keyboard's way through them: A approves, R rejects, E edits, J or → the
 * next, K or ← the one before, Escape drops a reason half typed.
 */
@Composable
private fun Review(
    state: ModerationState,
    actions: ModerationActions,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val review = state.review
    val question = review.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(review.rejecting, question?.id) {
        // A frame on, once the token's field, which held the focus as the token was taken, has gone.
        withFrameNanos {}
        if (!review.rejecting) focus.requestFocus()
    }
    val keys =
        Modifier
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || review.rejecting || state.busy) return@onKeyEvent false
                when (event.key) {
                    Key.A -> actions.approveCurrent()
                    Key.R -> actions.startReject()
                    Key.E -> question?.let(actions::edit)
                    Key.J, Key.DirectionRight -> actions.next()
                    Key.K, Key.DirectionLeft -> actions.previous()
                    else -> return@onKeyEvent false
                }
                true
            }
    val cardPane: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.verticalScroll(rememberScrollState())) {
            when {
                !review.loaded -> {
                    KvizicText("Loading the drafts…", style = type.body)
                }

                question == null -> {
                    KvizicText(
                        "No drafts waiting. Decided since unlocking: ${review.decided}.",
                        style = type.body,
                        color = colors.onPageMuted,
                    )
                }

                else -> {
                    QuestionCard(question, state, Modifier.fillMaxWidth())
                }
            }
        }
    }
    val actionPane: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(space.sm)) {
            if (question != null) {
                KvizicText(
                    "${review.index + 1} of ${review.drafts.size}${if (review.more) "+" else ""} · decided ${review.decided}",
                    style = type.label,
                    color = colors.onPageMuted,
                )
                val idle = !state.busy
                if (review.rejecting) {
                    val reasonFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { reasonFocus.requestFocus() }
                    TextInput(
                        value = review.reason,
                        onValueChange = actions::typeReason,
                        label = "Why reject it",
                        error = review.reason.takeIf { it.isNotEmpty() }?.let(QuestionRules::reasonProblem),
                        modifier =
                            Modifier.fillMaxWidth().onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                    actions.cancelReject()
                                    true
                                } else {
                                    false
                                }
                            },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { actions.rejectCurrent() }),
                        focusRequester = reasonFocus,
                    )
                    ReadyReasons(actions)
                    Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        StageButton(
                            "Reject",
                            onClick = actions::rejectCurrent,
                            size = ButtonSize.SMALL,
                            enabled = idle && QuestionRules.reasonProblem(review.reason) == null,
                        )
                        StageButton(
                            "Cancel",
                            onClick = actions::cancelReject,
                            kind = ButtonKind.QUIET,
                            size = ButtonSize.SMALL,
                        )
                    }
                } else {
                    StageButton(
                        "Approve  [A]",
                        onClick = actions::approveCurrent,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = idle,
                    )
                    StageButton(
                        "Reject…  [R]",
                        onClick = actions::startReject,
                        modifier = Modifier.fillMaxWidth(),
                        kind = ButtonKind.SECONDARY,
                        enabled = idle,
                    )
                    StageButton(
                        "Edit…  [E]",
                        onClick = { actions.edit(question) },
                        modifier = Modifier.fillMaxWidth(),
                        kind = ButtonKind.SECONDARY,
                        enabled = idle,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                        StageButton(
                            "← [K]",
                            onClick = actions::previous,
                            kind = ButtonKind.DARK,
                            size = ButtonSize.SMALL,
                        )
                        StageButton(
                            "Skip →  [J]",
                            onClick = actions::next,
                            kind = ButtonKind.DARK,
                            size = ButtonSize.SMALL,
                        )
                    }
                    KvizicText(
                        "On the web page, click it once after unlocking for the keys to work.",
                        style = type.caption,
                        color = colors.onPageMuted,
                    )
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < NARROW_WIDTH) {
            // A phone: the card over the actions, the card scrolling.
            Column(Modifier.fillMaxSize().then(keys), verticalArrangement = Arrangement.spacedBy(space.md)) {
                cardPane(Modifier.weight(1f).fillMaxWidth())
                actionPane(Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize().then(keys), horizontalArrangement = Arrangement.spacedBy(space.lg)) {
                cardPane(Modifier.weight(1f))
                actionPane(Modifier.width(space.dialogWidth))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyReasons(actions: ModerationActions) {
    val space = KvizicTheme.space
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space.xs),
        verticalArrangement = Arrangement.spacedBy(space.xs),
    ) {
        READY_REASONS.forEach { reason -> Chip(reason, onClick = { actions.typeReason(reason) }) }
    }
}

/** The whole bank, by status and search, a page at a time. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bank(
    state: ModerationState,
    actions: ModerationActions,
) {
    val space = KvizicTheme.space
    val bank = state.bank
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            TextInput(
                value = bank.search,
                onValueChange = actions::typeSearch,
                label = "Search the text and answers",
                modifier = Modifier.widthIn(max = space.contentWidth).weight(1f, fill = false),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.load(AdminTab.BANK) }),
            )
            StageButton(
                "Search",
                onClick = { actions.load(AdminTab.BANK) },
                size = ButtonSize.SMALL,
                enabled = !state.busy,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space.xs),
            verticalArrangement = Arrangement.spacedBy(space.xs),
        ) {
            BankStatus.entries.filter { it != BankStatus.OTHER }.forEach { status ->
                Chip(status.word(), selected = status in bank.statuses, onClick = { actions.toggleStatus(status) })
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(space.sm), modifier = Modifier.weight(1f)) {
            items(bank.questions, key = { it.id }) { question ->
                QuestionCard(question, state, Modifier.fillMaxWidth(), compact = true) {
                    MoveButtons(question, state, actions)
                }
            }
            item {
                when {
                    bank.next != null -> {
                        StageButton(
                            "Load more",
                            onClick = actions::loadMore,
                            kind = ButtonKind.SECONDARY,
                            enabled = !state.busy,
                        )
                    }

                    bank.loaded && bank.questions.isEmpty() -> {
                        KvizicText(
                            "Nothing matches.",
                            style = KvizicTheme.type.body,
                            color = KvizicTheme.colors.onPageMuted,
                        )
                    }
                }
            }
        }
    }
}

/** What can be done to [question] where it stands. */
@Composable
private fun MoveButtons(
    question: ModeratedQuestion,
    state: ModerationState,
    actions: ModerationActions,
) {
    val idle = !state.busy
    if (question.status == BankStatus.DRAFT || question.status == BankStatus.REJECTED) {
        StageButton("Approve", onClick = { actions.approve(question) }, size = ButtonSize.SMALL, enabled = idle)
    }
    if (question.status == BankStatus.APPROVED || question.status == BankStatus.SUSPENDED) {
        StageButton(
            "Retire",
            onClick = { actions.retire(question) },
            kind = ButtonKind.SECONDARY,
            size = ButtonSize.SMALL,
            enabled = idle,
        )
    }
    if (question.status == BankStatus.RETIRED || question.status == BankStatus.SUSPENDED) {
        StageButton("Restore", onClick = { actions.restore(question) }, size = ButtonSize.SMALL, enabled = idle)
    }
    StageButton("Edit…", onClick = {
        actions.edit(question)
    }, kind = ButtonKind.QUIET, size = ButtonSize.SMALL, enabled = idle)
}

/** The questions players reported, the most reported first, and how each is dealt with. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Reports(
    state: ModerationState,
    actions: ModerationActions,
) {
    val space = KvizicTheme.space
    val reports = state.reports
    if (reports.loaded && reports.reported.isEmpty()) {
        KvizicText("No open reports.", style = KvizicTheme.type.body, color = KvizicTheme.colors.onPageMuted)
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(space.md)) {
        items(reports.reported, key = { it.question.id }) { reported ->
            val question = reported.question
            Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                    verticalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    Chip("${reported.open} open", tone = ChipTone.LOSS)
                    reported.reasons.forEach { (reason, count) -> Chip("${reason.word()} $count") }
                }
                QuestionCard(question, state, Modifier.fillMaxWidth(), compact = true) {
                    val idle = !state.busy
                    StageButton("Edit…", onClick = {
                        actions.edit(question)
                    }, kind = ButtonKind.QUIET, size = ButtonSize.SMALL, enabled = idle)
                    StageButton(
                        "Mark fixed",
                        onClick = { actions.resolve(question, ReportOutcome.FIXED) },
                        size = ButtonSize.SMALL,
                        enabled = idle,
                    )
                    StageButton(
                        "Dismiss",
                        onClick = { actions.resolve(question, ReportOutcome.DISMISSED) },
                        kind = ButtonKind.SECONDARY,
                        size = ButtonSize.SMALL,
                        enabled = idle,
                    )
                    StageButton(
                        "Retire",
                        onClick = { actions.resolve(question, ReportOutcome.RETIRED) },
                        kind = ButtonKind.SECONDARY,
                        size = ButtonSize.SMALL,
                        enabled = idle,
                    )
                }
            }
        }
    }
}

/** The bank by status and approved questions by topic, and the server's live games. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(
    overview: BankOverview?,
    state: ModerationState,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    if (overview == null) {
        KvizicText("Loading…", style = type.body)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
                KvizicText("Live", style = type.label)
                KvizicText(
                    "${overview.connectedPlayers} players connected · ${overview.liveLobbies} rooms · " +
                        "${overview.liveGames} games under way · ${overview.gamesToday} games today",
                    style = type.body,
                )
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
                KvizicText("The bank", style = type.label)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                    verticalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    BankStatus.entries.forEach { status ->
                        overview.byStatus[status]?.let { Chip("${status.word()} $it") }
                    }
                }
                KvizicText("Approved by topic", style = type.label)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                    verticalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    val topics =
                        state.topics.map { it.id } +
                            overview.approvedByTopic.keys.filter { id -> state.topics.none { it.id == id } }
                    topics.forEach { id ->
                        Chip("${state.topicNames(listOf(id))} ${overview.approvedByTopic[id] ?: 0}")
                    }
                }
            }
        }
    }
}

/**
 * Every player: searched by a name's part or an id's start, ordered four ways, kept to those who answered or
 * signed in with Play Games, in one list that reads its next page as the end comes into view; a row opens the
 * account with what it has played and its deletion, and at the end stands the way to delete one by an id
 * typed from an email.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Accounts(
    state: ModerationState,
    actions: ModerationActions,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val accounts = state.accounts
    val now = remember(accounts.players, accounts.selected) { Clock.System.now().toEpochMilliseconds() }
    val list = rememberLazyListState()
    // Reads on as the last rows come into view: the end of what is shown is never far off a scroll.
    val nearEnd by remember {
        derivedStateOf {
            val last =
                list.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            last >= list.layoutInfo.totalItemsCount - READ_AHEAD
        }
    }
    LaunchedEffect(nearEnd, accounts.next, state.busy) {
        if (nearEnd && accounts.next != null && !state.busy) actions.loadMoreAccounts()
    }
    LazyColumn(
        modifier = Modifier.widthIn(max = space.contentWidth).fillMaxSize(),
        state = list,
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    TextInput(
                        value = accounts.search,
                        onValueChange = actions::typeAccountSearch,
                        label = "Search a name or the start of an id",
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { actions.load(AdminTab.ACCOUNTS) }),
                    )
                    StageButton(
                        "Search",
                        onClick = { actions.load(AdminTab.ACCOUNTS) },
                        size = ButtonSize.SMALL,
                        enabled = !state.busy,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(space.xs),
                    verticalArrangement = Arrangement.spacedBy(space.xs),
                ) {
                    AccountOrder.entries.forEach { order ->
                        Chip(
                            order.word(),
                            selected = order == accounts.order,
                            onClick = { actions.pickAccountOrder(order) },
                        )
                    }
                    Chip("Has answered", selected = accounts.answeredOnly, onClick = actions::toggleAnsweredOnly)
                    Chip("Play Games", selected = accounts.playGamesOnly, onClick = actions::togglePlayGamesOnly)
                }
                if (accounts.loaded) {
                    KvizicText(
                        "${accounts.players.size} of ${accounts.total} players",
                        style = type.label,
                        color = KvizicTheme.colors.onPageMuted,
                    )
                }
                accounts.selected?.let { AccountDetailCard(it, now, state, actions) }
            }
        }
        items(accounts.players, key = { it.id }) { player ->
            AccountRow(player, now, onOpen = { actions.openAccount(player.id) })
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                when {
                    accounts.next != null -> {
                        StageButton(
                            "Load more",
                            onClick = actions::loadMoreAccounts,
                            kind = ButtonKind.SECONDARY,
                            enabled = !state.busy,
                        )
                    }

                    accounts.loaded && accounts.players.isEmpty() -> {
                        KvizicText("Nobody matches.", style = type.body, color = KvizicTheme.colors.onPageMuted)
                    }
                }
                KvizicText(
                    "A player asks by email to have their account deleted, naming the account id the game's " +
                        "About screen shows. Nothing proves the email is theirs: the id is all it names.",
                    style = type.body,
                    color = KvizicTheme.colors.onPageMuted,
                )
                TextInput(
                    value = accounts.accountId,
                    onValueChange = actions::typeAccountId,
                    label = "Account id",
                    modifier = Modifier.fillMaxWidth(),
                )
                StageButton(
                    "Delete account…",
                    onClick = actions::askToDelete,
                    kind = ButtonKind.SECONDARY,
                    enabled = !state.busy && accounts.accountId.isNotBlank(),
                )
                EmptyAccountsCleanup(state, actions)
                accounts.done?.let { KvizicText(it, style = type.bodyStrong, color = KvizicTheme.colors.gain) }
            }
        }
    }
    if (accounts.confirmingCleanup) {
        StageDialog(
            onDismiss = actions::cancelCleanUp,
            title = "Delete ${accounts.emptyCount} empty accounts?",
            text =
                "No Play Games link, no game, no answer, idle ${accounts.cleanupDays} days or more. " +
                    "It cannot be undone.",
        ) {
            StageButton("Delete accounts", onClick = actions::cleanUpEmptyAccounts, size = ButtonSize.SMALL)
            StageButton("Cancel", onClick = actions::cancelCleanUp, kind = ButtonKind.QUIET, size = ButtonSize.SMALL)
        }
    }
    if (accounts.confirming) {
        StageDialog(
            onDismiss = actions::cancelDelete,
            title = "Delete account ${accounts.accountId.trim()}?",
            text = "Their profile, games, stats, reports and sessions on every device go, and it cannot be undone.",
        ) {
            StageButton("Delete account", onClick = actions::deleteAccount, size = ButtonSize.SMALL)
            StageButton("Cancel", onClick = actions::cancelDelete, kind = ButtonKind.QUIET, size = ButtonSize.SMALL)
        }
    }
}

/**
 * Clearing the accounts nobody plays, the platform's pre-launch bots among them: pick how long idle, count,
 * then delete exactly what the count showed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyAccountsCleanup(
    state: ModerationState,
    actions: ModerationActions,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val accounts = state.accounts
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        KvizicText("Empty accounts", style = type.label, color = KvizicTheme.colors.onPageMuted)
        KvizicText(
            "No Play Games link, no game played, no answer given, and not seen for the days picked.",
            style = type.body,
            color = KvizicTheme.colors.onPageMuted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space.xs),
            verticalArrangement = Arrangement.spacedBy(space.xs),
        ) {
            AccountsState.CLEANUP_DAYS.forEach { days ->
                Chip(
                    "$days d",
                    selected = days == accounts.cleanupDays,
                    onClick = { actions.pickCleanupDays(days) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            StageButton(
                "Count",
                onClick = actions::countEmptyAccounts,
                kind = ButtonKind.SECONDARY,
                size = ButtonSize.SMALL,
                enabled = !state.busy,
            )
            accounts.emptyCount?.let { count ->
                KvizicText("$count empty accounts", style = type.bodyStrong)
                if (count > 0) {
                    StageButton(
                        "Delete them…",
                        onClick = actions::askToCleanUp,
                        kind = ButtonKind.SECONDARY,
                        size = ButtonSize.SMALL,
                        enabled = !state.busy,
                    )
                }
            }
        }
    }
}

/** How many rows from the end of the list the next page is read at. */
private const val READ_AHEAD = 6
