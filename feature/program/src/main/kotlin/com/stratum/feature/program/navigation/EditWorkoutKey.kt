package com.stratum.feature.program.navigation

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.feature.program.ui.EditWorkoutRoute
import kotlinx.serialization.Serializable

/** Nav3 key for the Edit Workout screen. */
@Serializable
data class EditWorkoutKey(
    val workoutId: String,
    /** When non-null, loads/saves this program (library or active). Null = active program. */
    val programId: String? = null,
) : NavKey

/**
 * Registers the Edit Workout entry in a Nav3 [EntryProviderScope].
 */
fun EntryProviderScope<NavKey>.editWorkoutEntry(
    onBackClick: () -> Unit,
    onSwapLiftClick: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    onReorderLiftsClick: (workoutId: String) -> Unit,
    onNavigateToWorkout: (workoutId: String) -> Unit = {},
    onCreateEquipmentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    entry<EditWorkoutKey> { key ->
        EditWorkoutRoute(
            workoutId = key.workoutId,
            programId = key.programId,
            onBackClick = onBackClick,
            onSwapLiftClick = onSwapLiftClick,
            onReorderLiftsClick = onReorderLiftsClick,
            onNavigateToWorkout = onNavigateToWorkout,
            onCreateEquipmentClick = onCreateEquipmentClick,
            modifier = modifier,
        )
    }
}
