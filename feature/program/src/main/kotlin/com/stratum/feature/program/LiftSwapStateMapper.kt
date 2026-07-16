package com.stratum.feature.program

import com.stratum.core.ui.R
import com.stratum.core.ui.component.LiftLibraryFilter
import com.stratum.core.ui.component.LiftSwapDropdownOption
import com.stratum.core.ui.component.LiftSwapFilterIntent
import com.stratum.core.ui.component.LiftSwapOptionState
import com.stratum.core.ui.component.LiftSwapSheetState
import com.stratum.core.ui.component.defaultMuscleFilterTiers
import com.stratum.core.ui.component.toVolumeDisplayName
import com.stratum.domain.lift.LiftCategory
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.lift.ScoredLift
import com.stratum.domain.lift.classifyLiftCategory
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.feature.program.ui.WorkoutLiftLineState

import com.stratum.feature.program.ui.PLACEHOLDER_LIFT_NAME

/**
 * Domain → presentation mapping for the lift swap bottom sheet. Pure functions — no use-case
 * or repository access (ARCHITECTURE.md §4.4). The ViewModel passes the ranked candidate list
 * and the active [LiftSwapFilters] overlay; this file turns them into the immutable
 * [LiftSwapSheetState] the sheet renders.
 */
@Suppress("LongParameterList") // hoisted overlay + ranked list — single responsibility
internal fun buildLiftSwapSheetState(
    targetLiftName: String,
    ranked: List<ScoredLift>,
    filters: LiftSwapFilters,
): LiftSwapSheetState {
    val isAddMode = targetLiftName == PLACEHOLDER_LIFT_NAME

    val filtered =
        ranked
            .asSequence()
            .filter { scored -> filters.selectedMuscles.isEmpty() || scored.lift.primaryMuscles.any { it in filters.selectedMuscles } }
            .filter { scored -> filters.selectedBias == null || scored.lift.muscleLengthBias == filters.selectedBias }
            .filter { scored -> filters.selectedCategory == null || scored.lift.classifyLiftCategory() == filters.selectedCategory }
            .filter { scored ->
                when (filters.selectedLibrary) {
                    null -> true
                    LiftLibraryFilter.Custom -> scored.lift.isCustom
                    LiftLibraryFilter.StratumDefault -> !scored.lift.isCustom
                }
            }
            .filter { scored ->
                filters.searchQuery.isBlank() ||
                    scored.lift.name.contains(filters.searchQuery.trim(), ignoreCase = true)
            }
            .map { scored -> scored.toOptionState() }
            .toList()

    return LiftSwapSheetState(
        targetLiftName = targetLiftName,
        options = filtered,
        biasOptions = biasDropdownOptions(),
        categoryOptions = categoryDropdownOptions(),
        libraryOptions = libraryDropdownOptions(),
        muscleGroups = defaultMuscleFilterTiers(),
        selectedMuscles = filters.selectedMuscles,
        selectedBias = filters.selectedBias,
        selectedCategory = filters.selectedCategory,
        selectedLibrary = filters.selectedLibrary,
        searchQuery = filters.searchQuery,
        isAddMode = isAddMode,
    )
}



private fun ScoredLift.toOptionState(): LiftSwapOptionState =
    LiftSwapOptionState(
        id = lift.id.value,
        name = lift.name,
        categoryLabel = lift.classifyLiftCategory().displayName,
        primaryMuscleLabels = lift.primaryMuscles.map { muscle -> muscle.toVolumeDisplayName() },
        lengthBiasLabel = lift.muscleLengthBias.toDisplayLabel(),
        similarity = similarity.toFloat(),
        isCustom = lift.isCustom,
        imageUrl = lift.imageUris.firstOrNull(),
    )

private fun MuscleLengthBias.toDisplayLabel(): String =
    when (this) {
        MuscleLengthBias.Lengthened -> "Lengthened"
        MuscleLengthBias.MidRange -> "Mid"
        MuscleLengthBias.Shortened -> "Shortened"
    }

private fun biasDropdownOptions(): List<LiftSwapDropdownOption<MuscleLengthBias?>> =
    listOf(
        LiftSwapDropdownOption(value = null, label = "Any", leadingIconRes = R.drawable.ic_bias),
        LiftSwapDropdownOption(MuscleLengthBias.Lengthened, "Lengthened"),
        LiftSwapDropdownOption(MuscleLengthBias.MidRange, "Mid"),
        LiftSwapDropdownOption(MuscleLengthBias.Shortened, "Shortened"),
    )

private fun categoryDropdownOptions(): List<LiftSwapDropdownOption<LiftCategory?>> =
    listOf(
        LiftSwapDropdownOption(value = null, label = "All", leadingIconRes = R.drawable.ic_lift_type),
        LiftSwapDropdownOption(LiftCategory.CompoundUpper, LiftCategory.CompoundUpper.displayName),
        LiftSwapDropdownOption(LiftCategory.CompoundLower, LiftCategory.CompoundLower.displayName),
        LiftSwapDropdownOption(LiftCategory.AccessoryUpper, LiftCategory.AccessoryUpper.displayName),
        LiftSwapDropdownOption(LiftCategory.AccessoryLower, LiftCategory.AccessoryLower.displayName),
        LiftSwapDropdownOption(LiftCategory.Core, LiftCategory.Core.displayName),
    )

private fun libraryDropdownOptions(): List<LiftSwapDropdownOption<LiftLibraryFilter?>> =
    listOf(
        LiftSwapDropdownOption(value = null, label = "All", leadingIconRes = R.drawable.ic_library_books),
        LiftSwapDropdownOption(LiftLibraryFilter.Custom, "Custom"),
        LiftSwapDropdownOption(LiftLibraryFilter.StratumDefault, "Stratum Default"),
    )
