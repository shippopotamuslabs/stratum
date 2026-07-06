package com.stratum.feature.workout.navigation

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
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
    modifier: Modifier = Modifier,
) {
    entry<WorkoutKey> { key ->
        WorkoutRoute(
            workoutId = key.workoutId,
            onNavigateBack = onNavigateBack,
            onNavigateToFinalize = onNavigateToFinalize,
            onSwapLiftClick = onSwapLiftClick,
            modifier = modifier,
        )
    }
}
