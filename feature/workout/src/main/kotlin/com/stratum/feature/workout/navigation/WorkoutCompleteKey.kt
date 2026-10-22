package com.stratum.feature.workout.navigation

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.core.ui.theme.LocalStratumHazeState
import com.stratum.feature.workout.ui.EquipmentEditRequest
import com.stratum.feature.workout.ui.WorkoutCompleteRoute
import dev.chrisbanes.haze.rememberHazeState
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
    onEditEquipmentClick: (EquipmentEditRequest) -> Unit = {},
) {
    entry<WorkoutCompleteKey> { key ->
        // Glass host (STR-157, §12.8): one HazeState for this screen, provided at
        // the detail-route level — same host pattern as the session route.
        val hazeState = rememberHazeState()
        CompositionLocalProvider(LocalStratumHazeState provides hazeState) {
            WorkoutCompleteRoute(
                sessionId = key.sessionId,
                onClose = onCloseClick,
                modifier = modifier,
                onEditEquipmentClick = onEditEquipmentClick,
            )
        }
    }
}
