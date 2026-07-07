package com.stratum.feature.workout.navigation

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.feature.workout.ui.WorkoutCompleteRoute
import kotlinx.serialization.Serializable

/** Nav3 key for the workout completion screen. */
@Serializable
data class WorkoutCompleteKey(
    val sessionId: String,
) : NavKey

/**
 * Registers the WorkoutComplete entry in a Nav3 [EntryProviderScope].
 */
fun EntryProviderScope<NavKey>.workoutCompleteEntry(
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    entry<WorkoutCompleteKey> { key ->
        WorkoutCompleteRoute(
            sessionId = key.sessionId,
            onClose = onCloseClick,
            modifier = modifier,
        )
    }
}
