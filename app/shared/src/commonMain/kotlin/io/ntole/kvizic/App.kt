package io.ntole.kvizic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.ntole.kvizic.about.AboutScreen
import io.ntole.kvizic.about.AboutViewModel
import io.ntole.kvizic.about.DeleteAccountButton
import io.ntole.kvizic.about.Deletion
import io.ntole.kvizic.analytics.LocalAnalytics
import io.ntole.kvizic.analytics.UsageTracker
import io.ntole.kvizic.analytics.rememberConfigurationChanging
import io.ntole.kvizic.core.domain.error.KvizicException
import io.ntole.kvizic.core.domain.lobby.LobbyDifficulty
import io.ntole.kvizic.core.domain.lobby.LobbySessionState
import io.ntole.kvizic.core.domain.lobby.LobbySettings
import io.ntole.kvizic.core.domain.lobby.LobbyVisibility
import io.ntole.kvizic.core.domain.session.CurrentSession
import io.ntole.kvizic.core.domain.topic.GetTopics
import io.ntole.kvizic.core.domain.topic.Topic
import io.ntole.kvizic.core.domain.topic.TopicGroup
import io.ntole.kvizic.core.domain.topic.TopicRepository
import io.ntole.kvizic.core.domain.update.AppUpdate
import io.ntole.kvizic.design.component.Stage
import io.ntole.kvizic.home.HomeActions
import io.ntole.kvizic.home.HomeCounts
import io.ntole.kvizic.home.HomeScreen
import io.ntole.kvizic.home.HomeViewModel
import io.ntole.kvizic.home.Notice
import io.ntole.kvizic.home.ProfileScreen
import io.ntole.kvizic.language.Language
import io.ntole.kvizic.language.LanguageViewModel
import io.ntole.kvizic.language.LocalStrings
import io.ntole.kvizic.navigation.BackTopBar
import io.ntole.kvizic.navigation.Navigator
import io.ntole.kvizic.navigation.Screen
import io.ntole.kvizic.navigation.SystemBack
import io.ntole.kvizic.room.Entry
import io.ntole.kvizic.room.EntryWay
import io.ntole.kvizic.room.JoinScreen
import io.ntole.kvizic.room.PublicRoomsScreen
import io.ntole.kvizic.room.PublicRoomsViewModel
import io.ntole.kvizic.room.RoomActions
import io.ntole.kvizic.room.RoomScreen
import io.ntole.kvizic.room.RoomViewModel
import io.ntole.kvizic.room.SettingsScreen
import io.ntole.kvizic.room.entryFailureText
import io.ntole.kvizic.services.AppServices
import io.ntole.kvizic.share.LocalShareSheet
import io.ntole.kvizic.share.rememberShareSheet
import io.ntole.kvizic.theme.GameTheme
import io.ntole.kvizic.update.UpdateScreen
import io.ntole.kvizic.update.rememberUpdateButton
import kotlinx.coroutines.launch
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
        GameTheme(language) {
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

/**
 * The page under every screen, the whole window, the system bars' strips included; the screens inside the
 * safe drawing area, the system bars and a display cutout, never the gesture areas besides, which with
 * gesture navigation would take some 30 more on each side for nothing.
 */
@Composable
private fun Page(content: @Composable () -> Unit) {
    Stage(Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) { content() }
    }
}

/**
 * The screen on top of the back stack, and only it. Each screen's ViewModel belongs to the platform's own
 * owner, the activity's or the window's, never to the back stack, so a screen left and come back to shows
 * what it showed.
 *
 * The room is shown while the player is in one, and only then: a seat taken from any screen opens it over
 * Home, and the room letting the player go, or their leaving, goes back to Home, which says why.
 */
@Composable
private fun Screens(usage: UsageTracker) {
    val navigator = rememberSaveable(saver = Navigator.Saver) { Navigator() }
    val room = koinViewModel<RoomViewModel>()
    val roomState by room.state.collectAsStateWithLifecycle()
    val inRoom = roomState is LobbySessionState.InLobby
    SystemBack(enabled = navigator.canGoBack, onBack = { navigator.back() })
    LaunchedEffect(navigator.current) { usage.show(navigator.current.key) }
    LaunchedEffect(inRoom) { navigator.followRoom(inRoom) }
    LifecycleStartEffect(room) {
        room.wake()
        onStopOrDispose {}
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // The room the frame the player is in it, not once the stack follows a frame or two later.
        when (navigator.shownFor(inRoom)) {
            Screen.Home -> {
                Home(room, open = navigator::open)
            }

            Screen.About -> {
                BackTopBar(onBack = { navigator.back() }, title = LocalStrings.current.aboutScreen.title)
                Below { About(onDeleted = { navigator.back() }) }
            }

            Screen.Profile -> {
                BackTopBar(onBack = { navigator.back() }, title = LocalStrings.current.game.profile)
                Below { Profile() }
            }

            Screen.Join -> {
                BackTopBar(onBack = { navigator.back() }, title = LocalStrings.current.game.joinByCode)
                Below { Join(room) }
            }

            Screen.PublicRooms -> {
                BackTopBar(onBack = { navigator.back() }, title = LocalStrings.current.game.publicRooms)
                Below { PublicRooms(room, onCreate = { navigator.replace(Screen.NewRoom) }) }
            }

            Screen.NewRoom -> {
                BackTopBar(onBack = { navigator.back() }, title = LocalStrings.current.game.newRoom)
                Below { NewRoom(room) }
            }

            Screen.Room -> {
                (roomState as? LobbySessionState.InLobby)?.let { state ->
                    Room(room, state, onSettings = { navigator.open(Screen.RoomSettings) })
                }
            }

            Screen.RoomSettings -> {
                BackTopBar(onBack = { navigator.back() }, title = LocalStrings.current.game.settings)
                (roomState as? LobbySessionState.InLobby)?.let { state ->
                    Below { RoomSettings(room, state, onDone = { navigator.back() }) }
                }
            }

            // Never on the back stack: shown by App in place of every screen.
            Screen.Update -> {
                Unit
            }
        }
    }
}

/**
 * Home, its player read each time it is shown, the counts read again every few seconds while it is, why the
 * last room let the player go, and Quick play and solo taking a seat from here.
 */
@Composable
private fun Home(
    room: RoomViewModel,
    open: (Screen) -> Unit,
) {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rooms = koinViewModel<PublicRoomsViewModel>()
    val lobbies by rooms.lobbies.collectAsStateWithLifecycle()
    val presence by room.presence.collectAsStateWithLifecycle()
    val entry by room.entry.collectAsStateWithLifecycle()
    val roomState by room.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.shown() }
    PollWhileShown { rooms.poll(PublicRoomsViewModel.HOME_EVERY) }

    HomeScreen(
        state = state,
        actions =
            HomeActions(
                quickPlay = room::quickPlay,
                createRoom = { open(Screen.NewRoom) },
                joinByCode = { open(Screen.Join) },
                publicRooms = { open(Screen.PublicRooms) },
                solo = room::solo,
                about = { open(Screen.About) },
                profile = { open(Screen.Profile) },
                retry = viewModel::retry,
                dismissExit = room::leave,
                dismissFailure = room::dismissEntryFailure,
            ),
        counts =
            lobbies?.let {
                HomeCounts(
                    online = presence?.online ?: it.online,
                    searching = presence?.searching ?: it.searching,
                    publicRooms = it.lobbies.size,
                )
            },
        entry = entry.takeIf { it.isHomes() } ?: Entry.None,
        exit = (roomState as? LobbySessionState.Ended)?.exit,
    )
}

/** Whether Home is where this seat is taken from, or why it could not be shown: Quick play's and solo's. */
private fun Entry.isHomes(): Boolean = this == Entry.None || wayIs(EntryWay.QUICK_PLAY) || wayIs(EntryWay.SOLO)

private fun Entry.wayIs(way: EntryWay): Boolean =
    when (this) {
        Entry.None -> false
        is Entry.Taking -> this.way == way
        is Entry.Failed -> this.way == way
    }

/** Runs [poll] while the screen calling it is shown and the app started, and stops it otherwise. */
@Composable
private fun PollWhileShown(poll: suspend () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latest by rememberUpdatedState(poll)
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { latest() } }
}

/** The player's profile, read as Home reads it, and their avatar picked, sent at once when it is left. */
@Composable
private fun Profile() {
    val viewModel = koinViewModel<HomeViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val topics by rememberTopics()
    LaunchedEffect(viewModel) { viewModel.shown() }
    LifecycleStartEffect(viewModel) { onStopOrDispose { viewModel.keepAvatar() } }
    ProfileScreen(state, topics, onPick = viewModel::changeAvatar)
}

/** A room's code typed, and joined once all six digits are in. */
@Composable
private fun Join(room: RoomViewModel) {
    var code by rememberSaveable { mutableStateOf("") }
    val entry by room.entry.collectAsStateWithLifecycle()
    JoinScreen(
        code = code,
        onCode = {
            code = it
            room.dismissEntryFailure()
        },
        onJoin = { room.join(code) },
        entry = entry.takeIf { it.wayIs(EntryWay.JOIN) } ?: Entry.None,
        onDismissFailure = room::dismissEntryFailure,
    )
}

/** The public rooms, read again every few seconds while shown, each joined with a tap. */
@Composable
private fun PublicRooms(
    room: RoomViewModel,
    onCreate: () -> Unit,
) {
    val rooms = koinViewModel<PublicRoomsViewModel>()
    val lobbies by rooms.lobbies.collectAsStateWithLifecycle()
    val failure by rooms.failure.collectAsStateWithLifecycle()
    val entry by room.entry.collectAsStateWithLifecycle()
    PollWhileShown { rooms.poll(PublicRoomsViewModel.LIST_EVERY) }
    PublicRoomsScreen(
        lobbies = lobbies,
        failure = failure,
        entry = entry,
        onJoin = room::join,
        onQuickPlay = room::quickPlay,
        onCreate = onCreate,
        onDismissFailure = room::dismissEntryFailure,
    )
}

/** A new room's settings picked, the topics read as the screen is shown, and the room made with them. */
@Composable
private fun NewRoom(room: RoomViewModel) {
    var settings by rememberSaveable(stateSaver = LobbySettingsSaver) { mutableStateOf(LobbySettings()) }
    val topics by rememberTopics()
    val entry by room.entry.collectAsStateWithLifecycle()
    Column {
        val failed = entry as? Entry.Failed
        if (failed != null && failed.way == EntryWay.CREATE) {
            Notice(
                LocalStrings.current.entryFailureText(failed.error, failed.retryAfter),
                onDismiss = room::dismissEntryFailure,
            )
        }
        SettingsScreen(
            settings = settings,
            topics = topics,
            groups = rememberTopicGroups(),
            onChange = { settings = it },
            doneLabel =
                with(LocalStrings.current.game) {
                    if (entry ==
                        Entry.Taking(EntryWay.CREATE)
                    ) {
                        entering
                    } else {
                        create
                    }
                },
            onDone = { room.create(settings) },
            enabled = entry !is Entry.Taking,
        )
    }
}

/** The room the player is in, its topics read for their names, and its invitation shared. */
@Composable
private fun Room(
    room: RoomViewModel,
    state: LobbySessionState.InLobby,
    onSettings: () -> Unit,
) {
    val topics by rememberTopics()
    val note by room.note.collectAsStateWithLifecycle()
    val bursts by room.bursts.collectAsStateWithLifecycle()
    val share = LocalShareSheet.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    RoomScreen(
        state = state,
        topics = topics,
        note = note,
        bursts = bursts,
        actions =
            RoomActions(
                answer = room::answer,
                start = room::start,
                openSettings = onSettings,
                chooseDifficulty = { level -> room.updateSettings(state.lobby.settings.copy(difficulty = level)) },
                kick = room::kick,
                voteKick = room::voteKick,
                transferHost = room::transferHost,
                backToLobby = room::backToLobby,
                react = room::react,
                leave = room::leave,
                share = { text -> scope.launch { share.shareText(text) } },
                copyCode = { code ->
                    clipboard.setText(AnnotatedString(code))
                    room.codeCopied()
                },
                report = room::report,
            ),
    )
}

/** The host's change of the room's settings, from those it has, saved as the screen goes back. */
@Composable
private fun RoomSettings(
    room: RoomViewModel,
    state: LobbySessionState.InLobby,
    onDone: () -> Unit,
) {
    var settings by rememberSaveable(stateSaver = LobbySettingsSaver) { mutableStateOf(state.lobby.settings) }
    val topics by rememberTopics()
    SettingsScreen(
        settings = settings,
        topics = topics,
        groups = rememberTopicGroups(),
        onChange = { settings = it },
        doneLabel = LocalStrings.current.game.save,
        onDone = {
            room.updateSettings(settings)
            onDone()
        },
        minPlayers = state.lobby.members.size,
    )
}

/** The groups the topics are listed under, as [rememberTopics]'s read last brought them. */
@Composable
private fun rememberTopicGroups(): List<TopicGroup> {
    val repository = koinInject<TopicRepository>()
    val groups by repository.groups.collectAsStateWithLifecycle()
    return groups
}

/** Every topic, as last read, read again as the screen calling this is shown. */
@Composable
private fun rememberTopics(): State<List<Topic>> {
    val repository = koinInject<TopicRepository>()
    val getTopics = koinInject<GetTopics>()
    LaunchedEffect(getTopics) {
        try {
            getTopics()
        } catch (_: KvizicException) {
            // The names already read stay; a topic not read shows by its id.
        }
    }
    return repository.topics.collectAsStateWithLifecycle()
}

/** A room's settings kept through an activity made anew, a field each. */
private val LobbySettingsSaver: Saver<LobbySettings, Any> =
    listSaver(
        save = { s ->
            listOf(
                s.questionCount,
                s.secondsPerQuestion,
                s.topics.joinToString(","),
                s.maxPlayers,
                s.visibility.name,
                s.wrongAnswerPenalty,
                s.difficulty.name,
            )
        },
        restore = { saved ->
            LobbySettings(
                questionCount = saved[0] as Int,
                secondsPerQuestion = saved[1] as Int,
                topics = (saved[2] as String).split(',').filter { it.isNotEmpty() },
                maxPlayers = saved[3] as Int,
                visibility = LobbyVisibility.valueOf(saved[4] as String),
                wrongAnswerPenalty = saved[5] as Boolean,
                difficulty = LobbyDifficulty.valueOf(saved[6] as String),
            )
        },
    )

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
