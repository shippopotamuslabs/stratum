package com.stratum.feature.program.navigation

import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.stratum.feature.program.CreateProgramRoute
import kotlinx.serialization.Serializable

/**
 * Nav3 key for the Create Program wizard (and edit-existing via [programId]).
 *
 * @param startInScratch When true, skip the path chooser and open the scratch editor immediately
 *   (onboarding Build From Scratch).
 * @param finishOnboarding When true, successful Activate marks onboarding complete (first-run path).
 * @param programId When non-null, load that program into the Editor for modification.
 */
@Serializable
data class CreateProgramKey(
    val startInScratch: Boolean = false,
    val finishOnboarding: Boolean = false,
    val programId: String? = null,
) : NavKey

/**
 * Registers the Create Program entry in a Nav3 [EntryProviderScope].
 */
fun EntryProviderScope<NavKey>.createProgramEntry(
    onBackClick: () -> Unit,
    onFinished: () -> Unit,
    onSwapLiftClick: (workoutId: String, workoutLiftId: String?, liftId: String?, liftName: String?) -> Unit,
    onReorderLiftsClick: (workoutId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    entry<CreateProgramKey> { key ->
        CreateProgramRoute(
            startInScratch = key.startInScratch,
            finishOnboarding = key.finishOnboarding,
            programId = key.programId,
            onBackClick = onBackClick,
            onFinished = onFinished,
            onSwapLiftClick = onSwapLiftClick,
            onReorderLiftsClick = onReorderLiftsClick,
            modifier = modifier,
        )
    }
}
