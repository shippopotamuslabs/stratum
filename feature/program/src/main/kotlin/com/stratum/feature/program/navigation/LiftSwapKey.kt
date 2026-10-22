package com.stratum.feature.program.navigation

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.feature.program.ui.LiftSwapRoute
import kotlinx.serialization.Serializable

/** Nav3 key for the Lift Swap screen (converting the bottom sheet to a screen). */
@Serializable
data class LiftSwapKey(
    val workoutId: String,
    val workoutLiftId: String? = null,
    val liftId: String? = null,
    val liftName: String? = null,
    val commitMode: LiftSwapCommitMode = LiftSwapCommitMode.EditSession,
) : NavKey

/**
 * Registers the Lift Swap entry in a Nav3 [EntryProviderScope].
 */
fun EntryProviderScope<NavKey>.liftSwapEntry(
    onBackClick: () -> Unit,
    onCreateNewLift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    entry<LiftSwapKey> { key ->
        // Glass host (STR-158, §12.8): one HazeState for this route, provided at
        // the detail-route level — same host pattern as the workout routes.
        StratumGlassHost {
            LiftSwapRoute(
                workoutId = key.workoutId,
                workoutLiftId = key.workoutLiftId,
                liftId = key.liftId,
                liftName = key.liftName,
                commitMode = key.commitMode,
                onBackClick = onBackClick,
                onCreateNewLift = onCreateNewLift,
                modifier = modifier,
            )
        }
    }
}
