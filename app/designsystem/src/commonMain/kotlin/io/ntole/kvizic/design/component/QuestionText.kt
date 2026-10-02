package io.ntole.kvizic.design.component

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.design.skin.KvizicTheme

/**
 * A question's text, as large as it fits over its answers' tiles, while it is read and while it is answered: in
 * the question size over at most [ANSWERING_LINES], so a long question leaves the answers their room. It
 * shrinks in steps down to the skin's `questionMin`, the size at which the longest question the rules allow
 * still fits, and is cut only past that, on a screen smaller than any the game is drawn for.
 *
 * On the reveal ([recap]) the question was read already and the answers are what is looked at, so it is
 * set as strong body text, smaller still down to the caption's size when long, over at most
 * [ANSWERING_LINES].
 */
@Composable
fun QuestionText(
    text: String,
    modifier: Modifier = Modifier,
    recap: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
) {
    val type = KvizicTheme.type
    val style = if (recap) type.bodyStrong else type.question
    val least = if (recap) type.caption.style.fontSize else type.questionMin.style.fontSize
    KvizicText(
        text = text,
        modifier = modifier,
        style = style,
        textAlign = textAlign,
        maxLines = ANSWERING_LINES,
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = least,
                maxFontSize = style.style.fontSize,
            ),
    )
}

/** The most lines a question takes over its answers, which are what is read against the clock. */
const val ANSWERING_LINES: Int = 4
