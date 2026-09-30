package io.ntole.kvizic.core.network

import io.ntole.kvizic.core.domain.update.AppUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The game's [AppUpdate]: raised by the HTTP client the first time any answer is `UPGRADE_REQUIRED`
 * ([KvizicHttpClient.create]), and never lowered, since the server serves this build nothing more. The
 * realtime transport raises it too, on the socket's own close code for an old build.
 */
public class UpgradeSignal : AppUpdate {
    private val raised = MutableStateFlow(false)

    override val required: StateFlow<Boolean> = raised.asStateFlow()

    /** The server refused this build as too old. */
    public fun raise() {
        raised.value = true
    }
}
