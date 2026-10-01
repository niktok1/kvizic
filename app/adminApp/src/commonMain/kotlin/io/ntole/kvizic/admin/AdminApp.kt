package io.ntole.kvizic.admin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.ntole.kvizic.core.network.environment.KvizicEnvironment
import io.ntole.kvizic.design.skin.KvizicSkin
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** The moderation app in the game's own skin. [io.ntole.kvizic.admin.di.initAdminKoin] must have run first. */
@Composable
fun AdminApp() {
    val environment = koinInject<KvizicEnvironment>()
    val viewModel = koinViewModel<ModerationViewModel>()
    val state by viewModel.state.collectAsState()
    KvizicSkin {
        ModerationScreen(state, environment, viewModel)
    }
}

/** The desktop window's title, naming the server it acts on. */
fun windowTitleOf(environment: KvizicEnvironment): String = "Kvizić moderation · ${serverLineOf(environment)}"
