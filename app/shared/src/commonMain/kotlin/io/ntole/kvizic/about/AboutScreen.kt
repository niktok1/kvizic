package io.ntole.kvizic.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.ntole.kvizic.analytics.tapped
import io.ntole.kvizic.language.LocalLanguage
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.language.fill
import io.ntole.kvizic.theme.Shell

/**
 * The About screen: the game's name, its [version] and build number, the age it is for, links that open in
 * the browser to the site's privacy policy, terms, deleting an account and contact ([Site], in the language
 * shown), the player's [accountId], to copy and send when asking by email for their account to be deleted,
 * none while no session is stored, the Statistics switch, [statisticsOn] whether the player lets the game
 * send analytics, which [onStatisticsChange] changes, the libraries the game ships with, each with its
 * licence ([OPEN_SOURCE_LIBRARIES]), and last, quiet, [deletion], the way to delete the account. It
 * scrolls, its content held to a readable column on a wide window.
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
    val dimens = Shell.dimens
    val colors = MaterialTheme.colorScheme
    val shared = LocalStrings.current
    val strings = shared.aboutScreen
    val language = LocalLanguage.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            modifier = Modifier.widthIn(max = dimens.contentMaxWidth).fillMaxWidth().padding(dimens.screenPadding),
        ) {
            Text(
                text = shared.gameName,
                color = colors.primary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm),
            ) {
                Text(text = strings.version.fill(versionText(version)), color = colors.onSurfaceVariant)
                AgeRating()
            }

            HorizontalDivider()
            listOf(
                Triple(strings.privacy, SitePage.PRIVACY, "about.privacy"),
                Triple(strings.terms, SitePage.TERMS, "about.terms"),
                Triple(strings.deleteAccountPage, SitePage.DELETE_ACCOUNT, "about.deletion_page"),
                Triple(strings.contact, SitePage.CONTACT, "about.contact"),
            ).forEach { (label, page, element) -> Link(label, Site.url(page, language), element) }
            accountId?.let { id -> AccountId(id) }

            HorizontalDivider()
            StatisticsSwitch(on = statisticsOn, onChange = onStatisticsChange)

            HorizontalDivider()
            Text(
                text = strings.licences,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            OPEN_SOURCE_LIBRARIES.forEach { library -> Library(library) }

            HorizontalDivider()
            deletion()
        }
    }
}

/** The version as the screen shows it: MAJOR.MINOR.PATCH and the build number in brackets, when known. */
internal fun versionText(version: AppVersion): String = version.number?.let { "${version.name} ($it)" } ?: version.name

/** The age the game is for, on a pill, as the store listing and the terms say it. */
@Composable
private fun AgeRating() {
    val dimens = Shell.dimens
    val colors = MaterialTheme.colorScheme

    Surface(color = colors.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(
            text = AGE_RATING,
            color = colors.onSecondaryContainer,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = dimens.spaceSm, vertical = dimens.spaceXs),
        )
    }
}

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
                .clickable(role = Role.Button, onClick = tapped(element) { uriHandler.openIfAble(url) })
                .minimumInteractiveComponentSize(),
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
    }
}

/**
 * The player's account id, under a muted label, in a fixed width, and a button that copies it to the
 * clipboard; the label says it is copied beside it, on the same line, so nothing moves. A player sends it
 * to have their account deleted by email: a guest has no other name the server knows them by.
 */
@Composable
private fun AccountId(accountId: String) {
    val dimens = Shell.dimens
    val colors = MaterialTheme.colorScheme
    val strings = LocalStrings.current.aboutScreen

    // The one call Compose has for text in common code on every platform: its replacement's ClipEntry has
    // no common constructor.
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copied by remember(accountId) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spaceSm)) {
            Text(text = strings.accountId, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            if (copied) {
                Text(text = strings.copied, color = colors.primary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = accountId, color = colors.onSurface, style = Shell.codeStyle, modifier = Modifier.weight(1f))
            TextButton(
                onClick =
                    tapped("about.copy_account_id") {
                        clipboard.setText(AnnotatedString(accountId))
                        copied = true
                    },
                modifier = Modifier.semantics { contentDescription = strings.copyAccountId },
            ) {
                Text(strings.copy)
            }
        }
    }
}

/**
 * A library the game ships with, its licence, whose text a tap opens in the browser, and the copyright
 * notice its licence asks to ship with the app, where it asks for one.
 */
@Composable
private fun Library(library: Licensed) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    role = Role.Button,
                    onClick = tapped("about.licence") { uriHandler.openIfAble(library.licenceUrl) },
                ).minimumInteractiveComponentSize(),
    ) {
        Text(text = library.name, color = colors.onSurface)
        Text(text = library.licence, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        library.notice?.let { notice ->
            Text(text = notice, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
