package io.ntole.kvizic.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.ntole.kvizic.analytics.tappedAt
import io.ntole.kvizic.core.domain.player.Achievement
import io.ntole.kvizic.core.domain.player.Profile
import io.ntole.kvizic.core.region.ContentLanguages
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.LevelBar
import io.ntole.kvizic.design.component.Panel
import io.ntole.kvizic.design.component.PanelKind
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.failureText
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.room.nameIn

/**
 * The profile's titles: every achievement in [profile], its title, what earns it and how far the player is, and
 * for one earned a button that puts its title on, or takes off the one worn: [onWear] with none.
 */
@Composable
internal fun ProfileTitles(
    state: HomeState,
    profile: Profile,
    onWear: (String?) -> Unit,
) {
    val strings = LocalStrings.current
    val language = LocalLanguage.current
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val achievements = profile.achievements
    val wear =
        tappedAt("profile.title") { index -> onWear(achievements[index].id.takeUnless { it == profile.titleId }) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space.sm)) {
        KvizicText(strings.titles.heading, Modifier.fillMaxWidth(), style = type.label, color = colors.onPageMuted)
        state.titleFailure?.let { failure ->
            KvizicText(
                strings.failureText(failure.error, failure.retryAfter),
                style = type.caption,
                color = colors.loss,
            )
        }
        achievements.forEachIndexed { index, achievement ->
            val worn = achievement.id == profile.titleId
            Panel(Modifier.fillMaxWidth(), kind = if (worn) PanelKind.OWN else PanelKind.PLAIN, padding = space.md) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(space.md),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xxs)) {
                        KvizicText(
                            achievement.titleIn(language),
                            style = type.bodyStrong,
                            color = if (achievement.earned) colors.onRaised else colors.onRaisedMuted,
                        )
                        KvizicText(
                            wordsIn(achievement.how, language),
                            style = type.caption,
                            color = colors.onRaisedMuted,
                        )
                        if (!achievement.earned) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(space.sm),
                            ) {
                                LevelBar(
                                    achievement.progress.toFloat() / achievement.goal.coerceAtLeast(1),
                                    Modifier.weight(1f),
                                    contentDescription =
                                        strings.titles.progress.fill(
                                            achievement.progress,
                                            achievement.goal,
                                        ),
                                )
                                // Two counts are the same in every language, so they are no string of theirs.
                                KvizicText(
                                    "${achievement.progress} / ${achievement.goal}",
                                    style = type.caption,
                                    color = colors.onRaisedMuted,
                                )
                            }
                        }
                    }
                    if (achievement.earned) {
                        StageButton(
                            if (worn) strings.economy.takeOff else strings.economy.wear,
                            onClick = { wear(index) },
                            size = ButtonSize.SMALL,
                            kind = if (worn) ButtonKind.SECONDARY else ButtonKind.PRIMARY,
                            enabled = !state.wearingTitle,
                        )
                    }
                }
            }
        }
    }
}

/** The words of this achievement's title in [language]. */
internal fun Achievement.titleIn(language: Language): String = wordsIn(title, language)

private fun wordsIn(
    words: Map<String, String>,
    language: Language,
): String = nameIn(language, words[ContentLanguages.SERBIAN].orEmpty(), words[Language.ENGLISH.tag].orEmpty(), words)
