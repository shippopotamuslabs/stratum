package com.stratum.feature.program.navigation

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.feature.program.ui.ReorderWorkoutsRoute
import kotlinx.serialization.Serializable

/** Nav3 key for the Reorder Workouts (cycle days) screen — persist-only (Programs tab). */
@Serializable
data class ReorderWorkoutsKey(
    val programId: String,
) : NavKey

/**
 * Registers the Reorder Workouts entry in a Nav3 [EntryProviderScope].
 */
fun EntryProviderScope<NavKey>.reorderWorkoutsEntry(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    entry<ReorderWorkoutsKey> { key ->
        // Glass host (STR-158, §12.8): one HazeState for this route, provided at
        // the detail-route level — same host pattern as the workout routes.
        StratumGlassHost {
            ReorderWorkoutsRoute(
                programId = key.programId,
                onBackClick = onBackClick,
                modifier = modifier,
            )
        }
    }
}
