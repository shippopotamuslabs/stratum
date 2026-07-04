package com.stratum.feature.program

import com.stratum.domain.lift.LiftCategory
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Filter/search overlay held by [ProgramsViewModel] over the ranked candidate stream. Pure
 * data — the ViewModel reduces [com.stratum.core.ui.component.LiftSwapFilterIntent]s against
 * this, then [buildLiftSwapSheetState] applies it to produce the immutable sheet state.
 */
internal data class LiftSwapFilters(
    val selectedMuscles: Set<MuscleGroup> = emptySet(),
    val selectedBias: MuscleLengthBias? = null,
    val selectedCategory: LiftCategory? = null,
    val selectedLibrary: com.stratum.core.ui.component.LiftLibraryFilter? = null,
    val searchQuery: String = "",
) {
    val anyActive: Boolean
        get() =
            selectedMuscles.isNotEmpty() ||
                selectedBias != null ||
                selectedCategory != null ||
                selectedLibrary != null
}
