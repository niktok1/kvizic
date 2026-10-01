package io.ntole.kvizic.design.component

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.design.skin.KvizicTheme

/**
 * A question's text, as large as it fits: while it is read alone ([reading]), in the large reading size
 * over at most [READING_LINES] lines, and once the answers are up under it, in the question size over at
 * most [ANSWERING_LINES], so a long question leaves the answers their room. Either shrinks in steps down
 * to the skin's `questionMin`, the size at which the longest question the rules allow still fits both, and
 * is cut only past that, on a screen smaller than any the game is drawn for.
 *
 * On the reveal ([recap]) the question was read already and the answers are what is looked at, so it is
 * set as strong body text, smaller still down to the caption's size when long, over at most
 * [ANSWERING_LINES].
 */
@Composable
fun QuestionText(
    text: String,
    modifier: Modifier = Modifier,
    reading: Boolean = false,
    recap: Boolean = false,
    textAlign: TextAlign = if (reading) TextAlign.Center else TextAlign.Start,
) {
    val type = KvizicTheme.type
    val style =
        when {
            reading -> type.questionReading
            recap -> type.bodyStrong
            else -> type.question
        }
    val least = if (recap) type.caption.style.fontSize else type.questionMin.style.fontSize
    KvizicText(
        text = text,
        modifier = modifier,
        style = style,
        textAlign = textAlign,
        maxLines = if (reading) READING_LINES else ANSWERING_LINES,
        autoSize =
            TextAutoSize.StepBased(
                minFontSize = least,
                maxFontSize = style.style.fontSize,
            ),
    )
}

/** The most lines a question takes while read alone, when the whole stage is its. */
const val READING_LINES: Int = 7

/**
 * The most lines a question takes over its answers. It was read alone first, and the answers are now what
 * is read against the clock, so a long question gives them the room.
 */
const val ANSWERING_LINES: Int = 4
