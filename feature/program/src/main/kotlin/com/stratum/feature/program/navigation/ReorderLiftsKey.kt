package com.stratum.feature.program.navigation

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.feature.program.ui.ReorderLiftsRoute
import kotlinx.serialization.Serializable

/** Nav3 key for the Reorder Lifts screen. */
@Serializable
data class ReorderLiftsKey(
    val workoutId: String,
) : NavKey

/**
 * Registers the Reorder Lifts entry in a Nav3 [EntryProviderScope].
 */
fun EntryProviderScope<NavKey>.reorderLiftsEntry(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    entry<ReorderLiftsKey> { key ->
        // Glass host (STR-158, §12.8): one HazeState for this route, provided at
        // the detail-route level — same host pattern as the workout routes.
        StratumGlassHost {
            ReorderLiftsRoute(
                workoutId = key.workoutId,
                onBackClick = onBackClick,
                modifier = modifier,
            )
        }
    }
}
