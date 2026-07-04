package com.stratum.feature.program.navigation

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.feature.program.ui.LiftSwapRoute
import kotlinx.serialization.Serializable

/** Nav3 key for the Lift Swap screen (converting the bottom sheet to a screen). */
@Serializable
data class LiftSwapKey(
    val workoutLiftId: String,
    val liftId: String,
    val liftName: String,
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
        LiftSwapRoute(
            workoutLiftId = key.workoutLiftId,
            liftId = key.liftId,
            liftName = key.liftName,
            onBackClick = onBackClick,
            onCreateNewLift = onCreateNewLift,
            modifier = modifier,
        )
    }
}
