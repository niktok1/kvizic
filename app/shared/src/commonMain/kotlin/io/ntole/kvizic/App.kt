package io.ntole.kvizic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ntole.kvizic.about.AboutScreen
import io.ntole.kvizic.about.AboutViewModel
import io.ntole.kvizic.about.DeleteAccountButton
import io.ntole.kvizic.about.Deletion
import io.ntole.kvizic.analytics.LocalAnalytics
import io.ntole.kvizic.analytics.UsageTracker
import io.ntole.kvizic.analytics.rememberConfigurationChanging
import io.ntole.kvizic.core.domain.session.CurrentSession
import io.ntole.kvizic.core.domain.update.AppUpdate
import io.ntole.kvizic.home.HomeScreen
import io.ntole.kvizic.home.HomeViewModel
import io.ntole.kvizic.language.KvizicStrings
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.LanguageViewModel
import io.ntole.kvizic.navigation.BackTopBar
import io.ntole.kvizic.navigation.Navigator
import io.ntole.kvizic.navigation.Screen
import io.ntole.kvizic.navigation.SystemBack
import io.ntole.kvizic.services.AppServices
import io.ntole.kvizic.share.LocalShareSheet
import io.ntole.kvizic.share.rememberShareSheet
import io.ntole.kvizic.theme.ShellTheme
import io.ntole.kvizic.update.UpdateScreen
import io.ntole.kvizic.update.rememberUpdateButton
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app's root composable, identical on every platform: each entry point does nothing but call this, and
 * everything below here is shared. [io.ntole.kvizic.di.initKoin] must have run first; `startKoin`
 * publishes the Compose context, so no `KoinContext` wrapper is needed.
 *
 * The shell of the game, for now: Home, opened first, and the About screen, reached through a
 * [Navigator], a back stack made by hand, in the language kept on the device, Serbian Cyrillic until one
 * is picked. The app's comings and goings and every screen shown are reported to the analytics
 * ([UsageTracker]). Once the server serves this build nothing more, the one screen shown says a new
 * version is available ([AppUpdate]), whatever was shown before.
 */
@Composable
fun App() {
    val languages = koinViewModel<LanguageViewModel>()
    val language by languages.language.collectAsStateWithLifecycle()
    val usage = koinInject<UsageTracker>()
    ReportForegroundAndBackground(usage, language, services = koinInject())
    val updateRequired by koinInject<AppUpdate>().required.collectAsStateWithLifecycle()

    // Every tap on every screen is counted there, and what is shared goes through the platform's own sheet.
    CompositionLocalProvider(LocalAnalytics provides koinInject(), LocalShareSheet provides rememberShareSheet()) {
        ShellTheme {
            KvizicStrings(language) {
                Page {
                    if (updateRequired) {
                        LaunchedEffect(Unit) { usage.show(Screen.Update.key) }
                        UpdateScreen(button = rememberUpdateButton())
                    } else {
                        Screens(usage)
                    }
                }
            }
        }
    }
}

/**
 * The page under every screen, the whole window, the system bars' strips included; the screens inside the
 * safe drawing area, the system bars and a display cutout, never the gesture areas besides, which with
 * gesture navigation would take some 30 more on each side for nothing.
 */
@Composable
private fun Page(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background, contentColor = colors.onBackground, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { content() }
    }
}

/**
 * The screen on top of the back stack, and only it. Each screen's ViewModel belongs to the platform's own
 * owner, the activity's or the window's, never to the back stack, so a screen left and come back to shows
 * what it showed.
 */
@Composable
private fun Screens(usage: UsageTracker) {
    val navigator = rememberSaveable(saver = Navigator.Saver) { Navigator() }
    SystemBack(enabled = navigator.canGoBack, onBack = { navigator.back() })
    LaunchedEffect(navigator.current) { usage.show(navigator.current.key) }

    Column(modifier = Modifier.fillMaxSize()) {
        when (navigator.current) {
            Screen.Home -> {
                Home(onAbout = { navigator.open(Screen.About) })
            }

            Screen.About -> {
                BackTopBar(onBack = { navigator.back() })
                Below { About(onDeleted = { navigator.back() }) }
            }

            // Never on the back stack: shown by App in place of every screen.
            Screen.Update -> {
                Unit
            }
        }
    }
}

/** The placeholder Home, whose profile is read each time it is shown. */
@Composable
private fun Home(onAbout: () -> Unit) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.shown() }

    HomeScreen(state = state, onAbout = onAbout, onRetry = viewModel::retry)
}

/**
 * The About screen, with the account id of the session stored on the device, which it copies to send by
 * email for the account's deletion: read from the device and never from the server, so it shows offline
 * and mints no session. None while none is stored; and the new one should the device become another player
 * while it is shown. Once the account is deleted, [onDeleted] goes back, to a Home that reads the fresh
 * guest.
 */
@Composable
private fun About(onDeleted: () -> Unit) {
    val session = koinInject<CurrentSession>()
    val accountId by remember(session) { session.sessions }.collectAsStateWithLifecycle(session.current())
    val viewModel = koinViewModel<AboutViewModel>()
    val deletion by viewModel.deletion.collectAsStateWithLifecycle()

    // The player's choice, kept on the device, which the analytics hold.
    val analytics = LocalAnalytics.current
    val statisticsOn by analytics.enabled.collectAsStateWithLifecycle()

    LaunchedEffect(deletion) {
        if (deletion == Deletion.Done) {
            viewModel.leftAfterDeletion()
            onDeleted()
        }
    }

    AboutScreen(
        version = koinInject(),
        accountId = accountId,
        statisticsOn = statisticsOn,
        onStatisticsChange = analytics::setEnabled,
        deletion = { DeleteAccountButton(deletion = deletion, onDelete = viewModel::delete) },
    )
}

/**
 * The app coming to the foreground and going to the background, as the platform's lifecycle tells it, to
 * [usage]: Android's activity, the iOS view controller, the desktop window (minimized or not) and the
 * browser page (hidden or not). The app shown in [language]. [services] hear of each coming to the
 * foreground too, the launch's first, and start what runs by itself.
 */
@Composable
private fun ReportForegroundAndBackground(
    usage: UsageTracker,
    language: Language,
    services: AppServices,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val shownIn by rememberUpdatedState(language)
    val configurationChanging = rememberConfigurationChanging()
    DisposableEffect(lifecycle, usage, services) {
        fun cameToForeground() {
            usage.foreground(shownIn.tag)
            services.foreground()
        }
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> cameToForeground()
                    Lifecycle.Event.ON_STOP -> usage.background(configurationChanging())
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

/** A screen under its top bar, in the height the bar leaves it. */
@Composable
private fun ColumnScope.Below(screen: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f)) { screen() }
}
