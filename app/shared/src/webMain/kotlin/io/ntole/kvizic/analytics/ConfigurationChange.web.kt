package io.ntole.kvizic.analytics

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberConfigurationChanging(): () -> Boolean = { false }
