package io.ntole.kvizic.analytics

import androidx.compose.runtime.Composable

/**
 * Whether the app is stopping only to start again at once, asked as its lifecycle stops: Android's
 * activity made anew on a rotation, which [UsageTracker] lets pass. Nowhere else does
 * the app stop for that, so elsewhere it is never.
 */
@Composable
internal expect fun rememberConfigurationChanging(): () -> Boolean
