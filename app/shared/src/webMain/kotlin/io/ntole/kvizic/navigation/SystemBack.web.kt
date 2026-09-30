package io.ntole.kvizic.navigation

import androidx.compose.runtime.Composable

/** Nothing to bind: the screens' own buttons are the way back. */
@Composable
actual fun SystemBack(
    enabled: Boolean,
    onBack: () -> Unit,
) = Unit
