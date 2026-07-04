package com.stratum.core.ui.component

import androidx.annotation.DrawableRes
import com.stratum.domain.lift.LiftCategory
import com.stratum.domain.lift.MuscleLengthBias
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Immutable view state for [StratumLiftSwapBottomSheet]. Built by the feature ViewModel from
 * the [com.stratum.domain.lift.RankSwapCandidatesUseCase] stream, with filter and search state
 * overlaid (ARCHITECTURE.md §4.4 — only transient state lives in the ViewModel).
 *
 * The sheet itself holds no state; it is a pure function of this object plus the callbacks in
 * [LiftSwapFilterIntent]. The three downstream consumers (`:feature:program` + two planned) all
 * render the same shape, so the contract stays consistent across journeys.
 *
 * Note: this file lives under `/core/ui/component/` so it is exempt from the
 * `ForbiddenDomainTypeInUiState` Detekt rule (which scopes to `*UiState.kt` /
 * `*ViewState.kt` in feature modules only) — mirroring [StratumMuscleVolumeCardState], which
 * also embeds [MuscleGroup].
 *
 * @param targetLiftName injected into the title as `"Swapping $targetLiftName"`.
 * @param options already ranked by similarity and filtered by the active filter/search state.
 *  An empty list triggers the sheet's empty state.
 * @param biasOptions / [categoryOptions] / [libraryOptions] the dropdown option sets; each
 *  includes an "Any/All" sentinel keyed `null` to clear that axis.
 * @param muscleGroups the upper/lower muscle carousel split, paired into the two tiers.
 * @param selectedMuscles multi-select muscle filter set (empty = no muscle filter).
 * @param selectedBias / [selectedCategory] / [selectedLibrary] single-select filter axes;
 *  `null` means the axis is clear ("Any/All").
 * @param searchQuery current search text applied to [options].
 */
data class LiftSwapSheetState(
    val targetLiftName: String,
    val options: List<LiftSwapOptionState>,
    val biasOptions: List<LiftSwapDropdownOption<MuscleLengthBias?>>,
    val categoryOptions: List<LiftSwapDropdownOption<LiftCategory?>>,
    val libraryOptions: List<LiftSwapDropdownOption<LiftLibraryFilter?>>,
    val muscleGroups: LiftSwapMuscleTiers,
    val selectedMuscles: Set<MuscleGroup>,
    val selectedBias: MuscleLengthBias?,
    val selectedCategory: LiftCategory?,
    val selectedLibrary: LiftLibraryFilter?,
    val searchQuery: String,
) {
    /** True when any filter axis is active (drives the reset button's visibility). */
    val hasActiveFilter: Boolean
        get() =
            selectedMuscles.isNotEmpty() ||
                selectedBias != null ||
                selectedCategory != null ||
                selectedLibrary != null

    /**
     * The carousel key of the most-recently active dropdown filter, used by the swap sheet to
     * scroll that chip fully into view when it widens on selection. Order encodes priority:
     * `library` < `lift-type` < `bias` so multiple simultaneous selections scroll to the leading
     * one. `null` when no dropdown filter is active.
     */
    val activeFilterChipKey: String?
        get() = when {
            selectedBias != null -> "bias"
            selectedCategory != null -> "lift-type"
            selectedLibrary != null -> "library"
            else -> null
        }
}

/**
 * A single swap candidate row. [similarity] (0..1) is rendered as a subtle match indicator so
 * the user sees *why* a lift surfaced without a search. [imageUrl] is the optional primary
 * catalog image; when absent the row falls back to a tile showing the lift's initial.
 */
data class LiftSwapOptionState(
    val id: String,
    val name: String,
    val categoryLabel: String,
    val primaryMuscleLabels: List<String>,
    val lengthBiasLabel: String,
    val similarity: Float,
    val isCustom: Boolean,
    val imageUrl: String? = null,
    val enabled: Boolean = true,
)

/**
 * A dropdown option for a single-select filter axis. [value] is `null` for the "Any/All"
 * sentinel; otherwise it is the selected value. [leadingIconRes] is optional.
 */
data class LiftSwapDropdownOption<T>(
    val value: T,
    val label: String,
    @DrawableRes val leadingIconRes: Int? = null,
)

/** Custom vs Stratum-bundled lift origin. */
enum class LiftLibraryFilter { Custom, StratumDefault }

/**
 * Upper/lower muscle carousel split for the swap sheet's muscle-group filter. Both lists are
 * `StratumMuscleFilterCardState`s (display-name resolved by the ViewModel).
 */
data class LiftSwapMuscleTiers(
    val upper: List<StratumMuscleFilterCardState>,
    val lower: List<StratumMuscleFilterCardState>,
)

/**
 * Hoisted filter intents — the only way the sheet mutates filter state. The ViewModel reduces
 * these against its `MutableStateFlow` overlay; the sheet never persists anything itself.
 */
sealed interface LiftSwapFilterIntent {
    data class ToggleMuscle(val muscle: MuscleGroup) : LiftSwapFilterIntent
    data class SelectBias(val bias: MuscleLengthBias?) : LiftSwapFilterIntent
    data class SelectCategory(val category: LiftCategory?) : LiftSwapFilterIntent
    data class SelectLibrary(val library: LiftLibraryFilter?) : LiftSwapFilterIntent
    data object ResetAll : LiftSwapFilterIntent
}
