package io.ntole.kvizic.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import io.ntole.kvizic.core.domain.moderation.BankStatus
import io.ntole.kvizic.core.domain.moderation.ModeratedQuestion
import io.ntole.kvizic.core.domain.moderation.QuestionDifficulty
import io.ntole.kvizic.core.domain.moderation.QuestionRules
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.component.PanelKind
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.component.TextInput
import io.ntole.kvizic.design.skin.KvizicTheme

/**
 * A question as the moderator reviews it: where it stands, its text, every answer with the right one
 * marked, its topics, explanation and source, who wrote it and how it has played, and [buttons] under it.
 * [compact] sets the text smaller, for a list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun QuestionCard(
    question: ModeratedQuestion,
    state: ModerationState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    buttons: @Composable RowScope.() -> Unit = {},
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Panel(modifier, kind = if (compact) PanelKind.PLAIN else PanelKind.SCREEN, padding = space.md) {
        Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(question.status.word(), tone = toneOf(question.status))
                Chip(question.difficulty.word())
                KvizicText(state.topicNames(question.topics), style = type.label, color = colors.onRaisedMuted)
            }
            KvizicText(question.text, style = if (compact) type.bodyStrong else type.question)
            Answers(question, compact)
            question.explanation?.let { KvizicText(it, style = type.caption, color = colors.onRaisedMuted) }
            question.source?.let { source ->
                val uris = LocalUriHandler.current
                KvizicText(
                    source,
                    Modifier.clickable(role = Role.Button) { runCatching { uris.openUri(source) } },
                    style = type.caption,
                    color = colors.onRaisedMuted,
                    maxLines = 1,
                    textDecoration = TextDecoration.Underline,
                )
            }
            question.rejectionReason?.let {
                KvizicText("Rejected: $it", style = type.caption, color = colors.loss)
            }
            KvizicText(metaOf(question), style = type.caption, color = colors.onRaisedMuted)
            Row(
                horizontalArrangement = Arrangement.spacedBy(space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                buttons()
            }
        }
    }
}

/** Every answer with its letter, the right one marked: one under another, or on one line when [compact]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Answers(
    question: ModeratedQuestion,
    compact: Boolean,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val answers: @Composable () -> Unit = {
        question.options.forEachIndexed { index, option ->
            val right = index == question.correct
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.xs),
            ) {
                Chip(LETTERS.getOrElse(index) { "${index + 1}" }, tone = if (right) ChipTone.GAIN else ChipTone.NEUTRAL)
                KvizicText(
                    if (right) "$option · right" else option,
                    style = if (right) type.bodyStrong else type.body,
                    color = if (right) colors.gain else colors.onRaised,
                )
            }
        }
    }
    if (compact) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalArrangement = Arrangement.spacedBy(space.xs),
        ) {
            answers()
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(space.xs)) { answers() }
    }
}

private fun toneOf(status: BankStatus): ChipTone =
    when (status) {
        BankStatus.APPROVED -> ChipTone.GAIN
        BankStatus.REJECTED, BankStatus.SUSPENDED -> ChipTone.LOSS
        BankStatus.DRAFT -> ChipTone.ACCENT
        BankStatus.RETIRED, BankStatus.OTHER -> ChipTone.NEUTRAL
    }

/** Who wrote it, its batch and revision, and how it has played: the level it plays at when not its own. */
internal fun metaOf(question: ModeratedQuestion): String {
    val play = question.play
    val parts =
        buildList {
            if (question.author.isNotBlank()) add("by ${question.author}")
            question.importBatch?.let { add(it) }
            add("revision ${question.revision}")
            add("asked ${play.shown}")
            play.correctPercent?.let { add("$it% right of ${play.answered}") }
            if (play.unanswered > 0) add("${play.unanswered} silent")
            if (play.playsAs != question.difficulty && play.playsAs != QuestionDifficulty.OTHER) {
                add("plays ${play.playsAs.word().lowercase()}")
            }
            play.averageCorrectMillis?.let {
                add(
                    "right in ${it / MILLIS_PER_TENTH / TENTHS}.${it / MILLIS_PER_TENTH % TENTHS} s",
                )
            }
            if (question.openReports > 0) add("${question.openReports} open reports")
            add(question.id)
        }
    return parts.joinToString(" · ")
}

private const val MILLIS_PER_TENTH = 100
private const val TENTHS = 10

/**
 * A question edited, every field as the server checks it: the rules' first complaint, or the server's
 * refusal, shown over Save, which sends nothing until there is none.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Editor(
    editor: EditorState,
    state: ModerationState,
    actions: ModerationActions,
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    Column(
        Modifier.widthIn(max = space.contentWidth * 2).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        KvizicText("Edit the question", style = type.headline)
        TextInput(
            value = editor.text,
            onValueChange = { text -> actions.changeEditor { it.copy(text = text) } },
            label = "Question, at most ${QuestionRules.MAX_TEXT_LENGTH} characters (${editor.text.trim().length})",
            modifier = Modifier.fillMaxWidth(),
        )
        KvizicText("Answers: tap a letter to make it the right one", style = type.label, color = colors.onPageMuted)
        editor.options.forEachIndexed { index, option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                Chip(
                    LETTERS.getOrElse(index) { "${index + 1}" },
                    tone = if (index == editor.correct) ChipTone.GAIN else ChipTone.NEUTRAL,
                    selected = index == editor.correct,
                    onClick = { actions.changeEditor { it.copy(correct = index) } },
                )
                TextInput(
                    value = option,
                    onValueChange = { actions.typeOption(index, it) },
                    label = "Answer ${index + 1}${if (index == editor.correct) ", right" else ""}",
                    modifier = Modifier.weight(1f),
                )
                if (editor.options.size > QuestionRules.MIN_OPTIONS) {
                    StageButton(
                        "Remove",
                        onClick = { actions.removeOption(index) },
                        kind = ButtonKind.QUIET,
                        size = ButtonSize.SMALL,
                    )
                }
            }
        }
        if (editor.options.size < QuestionRules.MAX_OPTIONS) {
            StageButton("Add an answer", onClick = actions::addOption, kind = ButtonKind.QUIET, size = ButtonSize.SMALL)
        }
        KvizicText("Topics, 1 to ${QuestionRules.MAX_TOPICS}", style = type.label, color = colors.onPageMuted)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space.xs),
            verticalArrangement = Arrangement.spacedBy(space.xs),
        ) {
            val ids = state.topics.map { it.id } + editor.topics.filter { id -> state.topics.none { it.id == id } }
            ids.forEach { id ->
                Chip(
                    state.topicNames(listOf(id)),
                    selected = id in editor.topics,
                    onClick = { actions.toggleTopic(id) },
                )
            }
        }
        KvizicText("Difficulty", style = type.label, color = colors.onPageMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(space.xs)) {
            QuestionDifficulty.entries.filter { it != QuestionDifficulty.OTHER }.forEach { difficulty ->
                Chip(
                    difficulty.word(),
                    selected = difficulty == editor.difficulty,
                    onClick = { actions.pickDifficulty(difficulty) },
                )
            }
        }
        TextInput(
            value = editor.explanation,
            onValueChange = { text -> actions.changeEditor { it.copy(explanation = text) } },
            label = "Explanation, shown on the reveal (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
        TextInput(
            value = editor.source,
            onValueChange = { text -> actions.changeEditor { it.copy(source = text) } },
            label = "Source, a web address the moderator alone sees (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
        val changesPlay =
            editor.options.map { it.trim() } != editor.original.options || editor.correct != editor.original.correct
        if (changesPlay) {
            KvizicText(
                "Changing the answers or which is right starts its play counts afresh.",
                style = type.caption,
                color = colors.onPageMuted,
            )
        }
        editor.problem?.let { KvizicText(it, style = type.bodyStrong, color = colors.loss) }
        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            StageButton("Save", onClick = actions::saveEdit, enabled = !state.busy && editor.problem == null)
            StageButton("Cancel", onClick = actions::closeEditor, kind = ButtonKind.QUIET)
            Spacer(Modifier.weight(1f))
        }
    }
}
