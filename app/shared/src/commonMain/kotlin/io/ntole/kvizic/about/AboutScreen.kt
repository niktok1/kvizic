package io.ntole.kvizic.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.design.component.ButtonKind
import io.ntole.kvizic.design.component.ButtonSize
import io.ntole.kvizic.design.component.Chip
import io.ntole.kvizic.design.component.ChipTone
import io.ntole.kvizic.design.component.Divider
import io.ntole.kvizic.design.component.KvizicText
import io.ntole.kvizic.design.component.StageButton
import io.ntole.kvizic.design.skin.KvizicTheme
import io.ntole.kvizic.design.sound.Cue
import io.ntole.kvizic.design.sound.cued
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.fill

/**
 * The About screen: the game's name, its [version] and build number, the age it is for, links that open in
 * the browser to the site's privacy policy, terms, deleting an account and contact ([Site], in the language
 * shown), the libraries the game ships with, each with its licence ([OPEN_SOURCE_LIBRARIES]), and last,
 * together, what the game keeps of the player: the Statistics switch, [statisticsOn] whether the player lets
 * the game send analytics, which [onStatisticsChange] changes, the account's id, [accountId], to copy and send
 * when asking by email for the account to be deleted, none while no session is stored, and [deletion], the
 * quiet way to delete it. It scrolls, its content held to a readable column on a wide window.
 */
@Composable
fun AboutScreen(
    version: AppVersion,
    accountId: String?,
    modifier: Modifier = Modifier,
    statisticsOn: Boolean = true,
    onStatisticsChange: (Boolean) -> Unit = {},
    deletion: @Composable () -> Unit = {},
) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val shared = LocalStrings.current
    val strings = shared.aboutScreen
    val language = LocalLanguage.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(space.sm),
            modifier = Modifier.widthIn(max = space.contentWidth).fillMaxWidth().padding(space.screen),
        ) {
            KvizicText(text = shared.gameName, color = colors.onPageAccent, style = type.headline)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                KvizicText(text = strings.version.fill(versionText(version)), color = colors.onPageMuted)
                Chip(AGE_RATING, tone = ChipTone.ACCENT)
            }

            Divider()
            listOf(
                Triple(strings.privacy, SitePage.PRIVACY, "about.privacy"),
                Triple(strings.terms, SitePage.TERMS, "about.terms"),
                Triple(strings.deleteAccountPage, SitePage.DELETE_ACCOUNT, "about.deletion_page"),
                Triple(strings.contact, SitePage.CONTACT, "about.contact"),
            ).forEach { (label, page, element) -> Link(label, Site.url(page, language), element) }

            Divider()
            KvizicText(text = strings.licences, style = type.bodyStrong)
            OPEN_SOURCE_LIBRARIES.forEach { library -> Library(library) }

            Divider()
            KvizicText(text = strings.data, style = type.bodyStrong)
            StatisticsSwitch(on = statisticsOn, onChange = onStatisticsChange)
            accountId?.let { id -> AccountId(id) }
            deletion()
        }
    }
}

/** The version as the screen shows it: MAJOR.MINOR.PATCH and the build number in brackets, when known. */
internal fun versionText(version: AppVersion): String = version.number?.let { "${version.name} ($it)" } ?: version.name

/**
 * A line that opens [url] in the browser, reported as [element]'s tap, or does nothing when nothing on the
 * device opens it ([openIfAble]).
 */
@Composable
private fun Link(
    label: String,
    url: String,
    element: String,
) {
    val uriHandler = LocalUriHandler.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = cued(Cue.TAP_SOFT, tapped(element) { uriHandler.openIfAble(url) }),
                ).defaultMinSize(minHeight = KvizicTheme.space.touchTarget),
    ) {
        KvizicText(text = label, color = KvizicTheme.colors.onPageAccent, textDecoration = TextDecoration.Underline)
    }
}

/**
 * The player's account id, under a muted label, in a fixed width, and a button that copies it to the
 * clipboard; the label says it is copied beside it, on the same line, so nothing moves. A player sends it
 * to have their account deleted by email: a guest has no other name the server knows them by.
 */
@Composable
private fun AccountId(accountId: String) {
    val space = KvizicTheme.space
    val type = KvizicTheme.type
    val colors = KvizicTheme.colors
    val strings = LocalStrings.current.aboutScreen

    // The one call Compose has for text in common code on every platform: its replacement's ClipEntry has
    // no common constructor.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember(accountId) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            KvizicText(text = strings.accountId, color = colors.onPageMuted, style = type.caption)
            if (copied) {
                KvizicText(text = strings.copied, color = colors.onPageAccent, style = type.caption)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            KvizicText(text = accountId, style = type.caption, modifier = Modifier.weight(1f))
            StageButton(
                strings.copy,
                onClick =
                    tapped("about.copy_account_id") {
                        clipboard.setText(AnnotatedString(accountId))
                        copied = true
                    },
                modifier = Modifier.semantics { contentDescription = strings.copyAccountId },
                kind = ButtonKind.QUIET,
                size = ButtonSize.SMALL,
                cue = Cue.COPIED,
            )
        }
    }
}

/**
 * A library the game ships with, its licence, whose text a tap opens in the browser, and the copyright
 * notice its licence asks to ship with the app, where it asks for one.
 */
@Composable
private fun Library(library: Licensed) {
    val colors = KvizicTheme.colors
    val type = KvizicTheme.type
    val uriHandler = LocalUriHandler.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = cued(Cue.TAP_SOFT, tapped("about.licence") { uriHandler.openIfAble(library.licenceUrl) }),
                ).defaultMinSize(minHeight = KvizicTheme.space.touchTarget),
    ) {
        KvizicText(text = library.name)
        KvizicText(text = library.licence, color = colors.onPageMuted, style = type.caption)
        library.notice?.let { notice -> KvizicText(text = notice, color = colors.onPageMuted, style = type.caption) }
    }
}
