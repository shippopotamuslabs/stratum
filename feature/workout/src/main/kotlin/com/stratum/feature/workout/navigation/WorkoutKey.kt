package com.stratum.feature.workout.navigation

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.component.StratumGlassHost
import com.stratum.feature.workout.ui.EquipmentEditRequest
import com.stratum.feature.workout.ui.WorkoutRoute
import kotlinx.serialization.Serializable

/** Nav3 key for the live workout screen. */
@Serializable
data class WorkoutKey(
    val workoutId: String,
) : NavKey

/**
 * Registers the Workout entry in a Nav3 [EntryProviderScope].
 */
fun EntryProviderScope<NavKey>.workoutEntry(
    onNavigateBack: () -> Unit,
    onNavigateToFinalize: (sessionId: String) -> Unit,
    onSwapLiftClick: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    onEditEquipmentClick: (EquipmentEditRequest) -> Unit,
    onReorderLiftsClick: (workoutId: String) -> Unit,
    onCreateEquipmentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    entry<WorkoutKey> { key ->
        // Glass host (STR-157, §12.8): one HazeState for this screen, provided at
        // the detail-route level. The session screen (and ManageNotesScreen, which
        // it hosts) reads it through the scaffold seam; Neo keeps solid chrome via
        // the theme gate, and unhosted compositions stay solid.
        StratumGlassHost {
            WorkoutRoute(
                workoutId = key.workoutId,
                onNavigateBack = onNavigateBack,
                onNavigateToFinalize = onNavigateToFinalize,
                onSwapLiftClick = onSwapLiftClick,
                onEditEquipmentClick = onEditEquipmentClick,
                onReorderLiftsClick = onReorderLiftsClick,
                onCreateEquipmentClick = onCreateEquipmentClick,
                modifier = modifier,
            )
        }
    }
}
