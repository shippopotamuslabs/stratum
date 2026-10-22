package com.stratum.feature.program.navigation

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.feature.program.ProgramLibraryRoute
import kotlinx.serialization.Serializable

/** Nav3 key for the Program Library list. */
@Serializable
data object ProgramLibraryKey : NavKey

/**
 * Registers the Program Library entry in a Nav3 [EntryProviderScope].
 */
fun EntryProviderScope<NavKey>.programLibraryEntry(
    onBackClick: () -> Unit,
    onProgramClick: (programId: String) -> Unit,
    onWorkoutClick: (programId: String, workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    entry<ProgramLibraryKey> {
        // Glass host (STR-158, §12.8): one HazeState for this route, provided at
        // the detail-route level — same host pattern as the workout routes.
        StratumGlassHost {
            ProgramLibraryRoute(
                onBackClick = onBackClick,
                onProgramClick = onProgramClick,
                onWorkoutClick = onWorkoutClick,
                modifier = modifier,
            )
        }
    }
}
