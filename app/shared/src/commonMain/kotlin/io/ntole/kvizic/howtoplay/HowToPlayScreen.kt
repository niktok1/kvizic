package io.ntole.kvizic.howtoplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.KvizicIcon
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.icon.KvizicIcons
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.language.LocalStrings
import kotlinx.coroutines.launch

/**
 * How the game is played, on three cards swiped through or stepped through with the button under them: a
 * question read and then answered, with the others' picks seen once locked in; the points and the minus;
 * Quick play, a room of one's own and solo. A new install's first screen, and Settings' way back to it.
 *
 * [onSkip] leaves the cards from any of them, [onPlay] from the last. [firstCard] is the card shown first.
 */
@Composable
fun HowToPlayScreen(
    onSkip: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    firstCard: Int = 0,
) {
    val space = KvizicTheme.space
    val words = LocalStrings.current.howToPlay
    val cards =
        listOf(
            Card(KvizicIcons.Clock, words.questionTitle, words.questionBody),
            Card(KvizicIcons.Check, words.pointsTitle, words.pointsBody),
            Card(KvizicIcons.Person, words.roomsTitle, words.roomsBody),
        )
    val pager = rememberPagerState(firstCard) { cards.size }
    val scope = rememberCoroutineScope()
    val onLast = pager.currentPage == cards.lastIndex

    Box(contentAlignment = Alignment.TopCenter, modifier = modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space.md),
            modifier = Modifier.widthIn(max = space.contentWidth).fillMaxSize().padding(space.screen),
        ) {
            Box(contentAlignment = Alignment.CenterEnd, modifier = Modifier.fillMaxWidth()) {
                StageButton(
                    words.skip,
                    onClick = tapped("how_to_play.skip", onClick = onSkip),
                    kind = ButtonKind.QUIET,
                    size = ButtonSize.SMALL,
                )
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                CardFace(cards[page])
            }
            Dots(count = cards.size, current = pager.currentPage)
            StageButton(
                if (onLast) words.play else words.next,
                onClick =
                    if (onLast) {
                        tapped("how_to_play.play", onClick = onPlay)
                    } else {
                        tapped("how_to_play.next") {
                            scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        }
                    },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private class Card(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

@Composable
private fun CardFace(card: Card) {
    val space = KvizicTheme.space
    val colors = KvizicTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space.lg, Alignment.CenterVertically),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        KvizicIcon(card.icon, contentDescription = null, tint = colors.onPageAccent, size = space.icon.large)
        KvizicText(
            text = card.title,
            color = colors.onPageAccent,
            style = KvizicTheme.type.headline,
            textAlign = TextAlign.Center,
        )
        KvizicText(
            text = card.body,
            color = colors.onPage,
            style = KvizicTheme.type.body,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Dots(
    count: Int,
    current: Int,
) {
    val space = KvizicTheme.space
    val colors = KvizicTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(space.xs), modifier = Modifier.clearAndSetSemantics {}) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(space.sm)
                    .background(
                        if (index == current) colors.primary else colors.onPageMuted,
                        KvizicTheme.shapes.roundButton,
                    ),
            )
        }
    }
}
