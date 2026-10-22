package com.stratum.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.stratum.core.ui.theme.LocalStratumHazeState
import dev.chrisbanes.haze.rememberHazeState

/**
 * The glass host (§12.8): remembers one [dev.chrisbanes.haze.HazeState] and
 * provides it to the subtree via [LocalStratumHazeState]. This is the single
 * place host setup lives — nav entries and glass previews wrap their content
 * in it instead of repeating the remember + provider block, so any future
 * change to hosting (scoping, gating) lands once. Consumers read the state
 * through `rememberGlassHazeState()`, which applies the theme gate (Neo keeps
 * solid chrome) on top of whatever this host provides.
 */
@Composable
fun StratumGlassHost(content: @Composable () -> Unit) {
    val hazeState = rememberHazeState()
    CompositionLocalProvider(
        LocalStratumHazeState provides hazeState,
        content = content,
    )
}
